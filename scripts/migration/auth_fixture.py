"""Disposable PostgreSQL pair for the Java/Go auth migration."""
import json
import os
import re
import secrets
import subprocess
import tempfile
import time
import uuid
import urllib.request
from contextlib import ExitStack, contextmanager
from dataclasses import dataclass, field
from pathlib import Path

LABEL = "io.hellodoc.migration.auth"


def _docker(*args, env=None):
    docker_env = {"PATH": os.environ["PATH"], "HOME": os.environ["HOME"], **(env or {})}
    return subprocess.run(["docker", "--host", "unix:///var/run/docker.sock", *args], check=True, capture_output=True,
                          text=True, timeout=60, env=docker_env).stdout.strip()


def _create(name, run, password):
    env = {"POSTGRES_PASSWORD": password}
    try:
        cid = _docker("create", "--name", name, "--label", f"{LABEL}={run}",
                      "--tmpfs", "/var/lib/postgresql/data", "--publish", "127.0.0.1::5432",
                      "-e", "POSTGRES_PASSWORD", "-e", "POSTGRES_DB=auth_fixture",
                      "postgres:16", env=env)
        if not re.fullmatch(r"[0-9a-f]{64}", cid):
            raise RuntimeError("Docker did not return a full container ID")
    except (subprocess.SubprocessError, RuntimeError):
        # Docker might have completed the creation before its client lost the reply.
        try:
            identity = _identity(name)
        except subprocess.CalledProcessError as error:
            if "no such" not in error.stderr.lower():
                raise
        else:
            match = re.fullmatch(r"([0-9a-f]{64}) " + re.escape(run), identity)
            if match:
                _remove(match[1], run)
        raise
    return cid


def _identity(name_or_id):
    return _docker("inspect", "--format", '{{.Id}} {{index .Config.Labels "' + LABEL + '"}}', name_or_id)


def _remove(cid, run):
    if _identity(cid) != f"{cid} {run}":
        raise RuntimeError("Container ownership changed")
    _docker("rm", "-f", cid)


def _start(cid):
    _docker("start", cid)
    binding, = json.loads(_docker("inspect", "--format",
                                  '{{json (index .NetworkSettings.Ports "5432/tcp")}}', cid))
    if binding["HostIp"] != "127.0.0.1":
        raise RuntimeError("Unexpected PostgreSQL binding")
    for _ in range(150):
        try:
            _docker("exec", cid, "pg_isready", "-h", "127.0.0.1", "-d", "auth_fixture", "-U", "postgres")
            return int(binding["HostPort"])
        except subprocess.CalledProcessError:
            time.sleep(.2)
    raise RuntimeError("PostgreSQL failed to start")


@dataclass
class Target:
    container_id: str
    port: int
    directory: Path
    password: str = field(repr=False)
    account_password: str = field(default="", repr=False)
    user_id: int = 0
    catalog: dict = field(default_factory=dict)
    host: str = "127.0.0.1"
    database: str = "auth_fixture"
    username: str = "postgres"


@contextmanager
def auth_fixture():
    run = uuid.uuid4().hex
    with ExitStack() as stack:
        targets = {}
        for role in ("java", "go"):
            password = secrets.token_urlsafe(32)
            cid = _create(f"hellodoc-auth-{run}-{role}", run, password)
            stack.callback(_remove, cid, run)
            port = _start(cid)
            directory = Path(stack.enter_context(tempfile.TemporaryDirectory(prefix=f"auth-{role}-")))
            targets[role] = Target(cid, port, directory, password)
        _initialize_pair(targets)
        yield targets


ROOT = Path(__file__).resolve().parents[2]
TABLES = ("sys_user", "sys_user_auth", "sys_user_role", "sys_role", "sys_login_log", "sys_refresh_token")


def catalog(target):
    table_list = ", ".join(f"'{table}'" for table in TABLES)
    queries = {
        "columns": f"""select table_name, column_name, udt_name, is_nullable from information_schema.columns
            where table_schema='public' and table_name in ({table_list}) order by table_name, ordinal_position""",
        "sequences": f"""select table_name, column_name, pg_get_serial_sequence(table_name,column_name)
            from information_schema.columns where table_schema='public' and table_name in ({table_list})
            and pg_get_serial_sequence(table_name,column_name) is not null order by 1,2""",
        "foreign_keys": f"""select conrelid::regclass, conname, pg_get_constraintdef(oid)
            from pg_constraint where contype='f' and conrelid::regclass::text in ({table_list}) order by conrelid::regclass::text, conname""",
        "flyway": "select version,script,success from flyway_schema_history order by installed_rank",
        "row_counts": " union all ".join(f"select '{table}',count(*) from {table}" for table in TABLES),
    }
    return {name: _docker("exec", target.container_id, "psql", "-XAt", "-v", "ON_ERROR_STOP=1",
                          "-U", target.username, "-d", target.database, "-c", sql).splitlines()
            for name, sql in queries.items()}


def _initialize_pair(targets):
    jar = ROOT / "hellodoc-server/build/libs/hellodoc.jar"
    if not jar.is_file():
        raise RuntimeError("Build hellodoc-server with ./gradlew bootJar before starting the fixture")
    # Both sides see the same synthetic business data and credential, generated for this run.
    account = {"username": "migration_fixture", "nickname": "Migration Fixture",
               "email": "migration@example.invalid", "password": secrets.token_urlsafe(24)}
    for target in targets.values():
        env = {"PATH": os.environ["PATH"], "HOME": str(target.directory),
               "DB_URL": f"jdbc:postgresql://127.0.0.1:{target.port}/{target.database}",
               "DB_USERNAME": target.username, "DB_PASSWORD": target.password,
               "ADMIN_PASSWORD": secrets.token_urlsafe(32), "JWT_SECRET": secrets.token_hex(48),
               "OPENAI_API_KEY": "unused", "OPENAI_BASE_URL": "http://127.0.0.1:9",
               "OPENAI_MODEL": "unused", "AI_AGENT": "unused", "SERVER_ADDRESS": "127.0.0.1", "SERVER_PORT": "0"}
        log = target.directory / "startup.log"
        with log.open("w") as output:
            proc = subprocess.Popen(["java", "-jar", str(jar)], cwd=target.directory, env=env,
                                    stdout=output, stderr=subprocess.STDOUT)
            try:
                for _ in range(600):
                    contents = log.read_text(errors="replace")
                    match = re.search(r"Tomcat started on port (\d+)", contents)
                    if match:
                        break
                    if proc.poll() is not None:
                        raise RuntimeError("Java fixture exited before HTTP startup")
                    time.sleep(.1)
                else:
                    raise RuntimeError("Java initialization timed out")
                uri = f"http://127.0.0.1:{match[1]}/api/auth/register"
                opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
                req = urllib.request.Request(uri, json.dumps(account).encode(), {"Content-Type": "application/json"})
                with opener.open(req, timeout=15) as response:
                    registered = json.load(response)
                if registered.get("code") != 0:
                    raise RuntimeError(f"Java fixture registration returned code {registered.get('code')}")
                target.account_password = account["password"]
                target.user_id = registered["data"]["id"]
                target.catalog = catalog(target)
            finally:
                proc.terminate()
                try:
                    proc.wait(10)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait()
