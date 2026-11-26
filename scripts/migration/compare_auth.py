"""Compare the H2 wire capture to sequential Java/Go PostgreSQL handoffs."""
import base64
from contextlib import contextmanager
from copy import deepcopy
from datetime import datetime
import hashlib
import hmac
import json
import os
import re
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

from auth_fixture import ROOT, auth_fixture, catalog
from test_auth_postgres import fixture_sql
from system_info_http import AUTH, SECURITY


class ContractMismatch(ValueError):
    pass


def check(field, expected, observed):
    if json.dumps(expected, sort_keys=True) != json.dumps(observed, sort_keys=True):
        raise ContractMismatch(f"contract mismatch: {field}")


@contextmanager
def service(engine, target, secret, binary):
    env = dict(PATH=os.environ["PATH"], HOME=str(target.directory),
               SERVER_ADDRESS="127.0.0.1", SERVER_PORT="0", HELLODOC_GO_ADDR="127.0.0.1:0",
               HELLODOC_GO_AUTH_ENABLED="true", DB_USERNAME=target.username, DB_PASSWORD=target.password,
               DB_URL=f"jdbc:postgresql://127.0.0.1:{target.port}/{target.database}",
               JWT_SECRET=secret, ADMIN_PASSWORD=secrets.token_hex(32), AI_AGENT="unused",
               OPENAI_API_KEY="unused", OPENAI_MODEL="unused", OPENAI_BASE_URL="http://127.0.0.1:9")
    command = [str(binary)] if engine == "go" else ["java", "-jar", str(ROOT / "hellodoc-server/build/libs/hellodoc.jar")]
    log = target.directory / "service.log"
    with log.open("w") as output:
        proc = subprocess.Popen(command, cwd=target.directory, env=env, stdout=output, stderr=subprocess.STDOUT)
        try:
            for _ in range(300):
                if proc.poll() is not None:
                    raise RuntimeError("fixture service exited")
                if engine == "java":
                    port = re.search(r"Tomcat started on port (\d+)", log.read_text(errors="replace"))
                else:
                    listeners = subprocess.run(["lsof", "-Pan", "-p", str(proc.pid), "-a", "-iTCP", "-sTCP:LISTEN"],
                                               capture_output=True, text=True, timeout=5)
                    port = re.search(r"127\.0\.0\.1:(\d+) ", listeners.stdout)
                if port:
                    yield f"http://127.0.0.1:{port[1]}"
                    break
                time.sleep(.2)
            else:
                raise RuntimeError("fixture service startup timed out")
        finally:
            proc.terminate()
            try:
                proc.wait(10)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait()


def decode(part):
    return base64.urlsafe_b64decode(part + "=" * (-len(part) % 4))


def claims(raw, secret, user, refresh):
    header, payload, signature = raw.split(".")
    metadata = json.loads(decode(header))
    expected_header = {"alg": "HS512"}
    if "typ" in metadata:
        expected_header["typ"] = "JWT"
    check("JWT header", expected_header, metadata)
    if not hmac.compare_digest(decode(signature), hmac.digest(secret.encode(), f"{header}.{payload}".encode(), hashlib.sha512)):
        raise ContractMismatch("contract mismatch: JWT signature")
    result = json.loads(decode(payload))
    validate_claims(result, user, refresh)
    return result


def validate_claims(result, user, refresh):
    expected = {"sub": user, "iat": result["iat"], "exp": result["exp"]}
    check("issuance time", True, abs(time.time() - result["iat"]) < 60)
    check("TTL", (7 if refresh else 1) * 86400, result["exp"] - result["iat"])
    if refresh:
        for field in ("family", "jti"):
            expected[field] = str(uuid.UUID(result[field]))
        expected["type"] = "refresh"
    else:
        expected["roles"] = ["ROLE_user"]
    check("signed claims", expected, result)


def request(base, path, locale, body=None, bearer=None, media="application/json"):
    headers = {"Accept-Language": locale, "Content-Type": media}
    if bearer:
        headers["Authorization"] = "Bearer " + bearer
    req = urllib.request.Request(base + path, None if body is None else json.dumps(body).encode(), headers)
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args):
            return None
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    try:
        response = opener.open(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        expected_headers = {**SECURITY, "Content-Language": "en-US" if locale == "en" else "zh-CN",
                            "WWW-Authenticate": AUTH if response.status == 401 else None}
        for name, expected in expected_headers.items():
            actual = response.headers.get_all(name)
            if name == "Vary":
                actual = sorted(item.strip() for line in actual or [] for item in line.split(","))
                check(name, sorted(expected.split(", ")), actual)
            else:
                check(name, [expected] if expected else None, actual)
        return {"status": response.status, "contentType": response.headers["Content-Type"], "body": json.load(response)}


def validate_response(name, expected, result, secret, user):
    normalized = deepcopy(result)
    data = normalized["body"].get("data")
    if isinstance(data, dict):
        for field in ("accessToken", "refreshToken"):
            if field in data:
                claims(data[field], secret, user, field == "refreshToken")
                data[field] = "<jwt>"
        if "id" in data:
            check("positive user id", True, type(data["id"]) is int and data["id"] > 0)
            data["id"] = "<id>"
        for field in ("createTime", "updateTime"):
            if field in data:
                check("registration timestamp", True, datetime.fromisoformat(data[field]).tzinfo is not None)
                data[field] = "<instant>"
    check(name, expected, normalized)


def validate_session(parent, child, observed, logged_out):
    expected = [parent, child, "ROTATED", "LOGOUT" if logged_out else "", "1" if logged_out else "0"]
    check("committed session", expected, observed.split("|"))


def run_track(target, engines, binary, capture):
    secret = secrets.token_hex(48)
    sentinel = uuid.uuid4().hex
    fixture_sql(target, f"INSERT INTO kb_knowledge_base(title,owner_id) VALUES ('{sentinel}',{int(target.user_id)})")
    preservation_query = f"""SELECT row_to_json(u)::text, row_to_json(k)::text FROM sys_user u
                            JOIN kb_knowledge_base k ON k.owner_id=u.id WHERE u.id={int(target.user_id)}
                            AND k.title='{sentinel}'"""
    before = fixture_sql(target, preservation_query)
    check("synthetic resource", True, bool(before))
    for locale, vectors in capture.items():
        user = uuid.uuid4().hex
        credentials = {"username": user, "nickname": "Migration User", "password": secrets.token_urlsafe(32)}
        observed = set()

        def case(name, body, bearer=None, media="application/json"):
            result = request(base, "/api/auth/" + name.split(".")[0], locale, body, bearer, media)
            validate_response(name, vectors[name], result, secret, user)
            observed.add(name)
            return result["body"]["data"] if "data" in result["body"] else None

        with service(engines[0], target, secret, binary) as base:
            identity = case("register.success", credentials)["id"]
            case("register.duplicate", credentials)
            login = case("login.success", credentials)
            old = claims(login["refreshToken"], secret, user, True)
            logs = fixture_sql(target, f"""SELECT count(*) FROM sys_login_log l JOIN sys_user_auth a ON a.id=l.auth_id
                WHERE l.user_id=a.user_id AND l.success AND a.identifier='{user}' AND a.user_id={identity}""")
            check("login audit", "1", logs)
            issued = fixture_sql(target, f"""SELECT count(*) FROM sys_refresh_token WHERE user_id={identity}
                AND jti='{old["jti"]}' AND family_id='{old["family"]}' AND parent_jti IS NULL
                AND replaced_by_jti IS NULL AND used_at IS NULL AND revoked_at IS NULL
                AND abs(extract(epoch FROM expires_at)-{int(old["exp"])}) < 1""")
            check("initial session", "1", issued)
            roles = fixture_sql(target, f"""SELECT r.role_code FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id
                WHERE ur.user_id={identity} ORDER BY r.role_code""")
            check("stored roles", "user", roles)
        with service(engines[1], target, secret, binary) as base:
            info = request(base, "/api/system/info", locale, bearer=login["accessToken"])
            check("public access", 200, info["status"])
            check("public health", "UP", info["body"]["status"])
            rotated = case("refresh-token.success", {"refreshToken": login["refreshToken"]})
            child = claims(rotated["refreshToken"], secret, user, True)
            check("family continuity", old["family"], child["family"])
            check("distinct jti", True, old["jti"] != child["jti"])
            session_query = f"""SELECT c.parent_jti, p.replaced_by_jti, p.revoked_reason,
                    c.revoked_reason, (c.revoked_at IS NOT NULL)::int
                FROM sys_refresh_token p, sys_refresh_token c
                WHERE p.jti='{old["jti"]}' AND c.jti='{child["jti"]}' AND p.family_id=c.family_id
                    AND c.family_id='{child["family"]}' AND p.user_id=c.user_id AND p.user_id={identity}
                    AND p.parent_jti IS NULL AND p.used_at IS NOT NULL AND p.revoked_at IS NOT NULL
                    AND abs(extract(epoch FROM c.expires_at)-{int(child["exp"])}) < 1"""
            def check_session(logged_out):
                validate_session(old["jti"], child["jti"], fixture_sql(target, session_query), logged_out)
            check_session(False)
            refresh = {"refreshToken": rotated["refreshToken"]}
            case("logout.success", refresh)
            check_session(True)
            case("refresh-token.afterLogout", refresh)
            case("login.wrongPassword", {**credentials, "password": "incorrect"})
            case("refresh-token.invalid", {"refreshToken": "invalid"})
            for endpoint in ("register", "login", "refresh-token", "logout"):
                case(endpoint + ".missingFields", {})
                case(endpoint + ".wrongMedia", {}, media="text/plain")
                case(endpoint + ".invalidBearer", credentials, bearer="invalid")
        check("matrix coverage", sorted(vectors), sorted(observed))
        print(f"{engines[0]}/PG -> {engines[1]}/PG ({locale}): {len(observed)} H2 capture vectors passed", flush=True)
    check("Flyway history", target.catalog["flyway"], catalog(target)["flyway"])
    check("fixture user and resource", before, fixture_sql(target, preservation_query))


def main():
    capture = json.loads((ROOT / "tests/migration/fixtures/auth_contract.json").read_text())
    with auth_fixture() as pair:
        check("independent PostgreSQL instances", True, pair["java"].container_id != pair["go"].container_id)
        binary = pair["go"].directory / "hellodoc-go"
        subprocess.run(["go", "build", "-o", str(binary), "./cmd/hellodoc"], cwd=ROOT / "hellodoc-go", check=True)
        for source, destination in (("java", "go"), ("go", "java"), ("java", "java"), ("go", "go")):
            run_track(pair[source], (source, destination), binary, capture)


def cli():
    try:
        main()
    except Exception as error:
        # Subprocess errors can include fixture-only credentials or session identifiers.
        message = str(error) if isinstance(error, ContractMismatch) else type(error).__name__
        print("auth comparator failed: " + message, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(cli())
