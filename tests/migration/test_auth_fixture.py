import json
import os
import socket
import time
import urllib.request
import subprocess
import unittest
import uuid
from unittest.mock import patch

from scripts.migration import auth_fixture as fixture


class AuthFixtureTest(unittest.TestCase):
    def test_arbitrary_target_rejected(self):
        with self.assertRaises(TypeError):
            with fixture.auth_fixture("postgres://external"):
                pass

    def test_second_create_failure_releases_first(self):
        first = "a" * 64
        with patch.object(fixture, "_create", side_effect=[first, RuntimeError("collision")]), \
                patch.object(fixture, "_start", return_value=12345), \
                patch.object(fixture, "_remove") as remove:
            with self.assertRaisesRegex(RuntimeError, "collision"):
                with fixture.auth_fixture():
                    pass
            self.assertEqual([call.args[0] for call in remove.call_args_list], [first])

    def test_replaced_container_or_label_preserved(self):
        cid = "b" * 64
        for identity in ["c" * 64 + " run", cid + " old"]:
            with self.subTest(identity=identity), patch.object(fixture, "_docker", return_value=identity) as docker:
                with self.assertRaises(RuntimeError):
                    fixture._remove(cid, "run")
                self.assertEqual(docker.call_count, 1)

    def test_timed_out_create_recovers_only_owned_container(self):
        cid = "a" * 64
        with patch.object(fixture, "_docker", side_effect=[
                subprocess.TimeoutExpired(["docker", "create"], 60), cid + " run", cid + " run", ""]) as docker:
            with self.assertRaises(subprocess.TimeoutExpired):
                fixture._create("generated", "run", "synthetic")
            self.assertEqual(docker.call_args.args, ("rm", "-f", cid))

    def test_ambiguous_create_waits_for_container_visibility(self):
        cid = "a" * 64
        missing = subprocess.CalledProcessError(1, ["docker", "inspect"], stderr="No such object")
        unavailable = subprocess.TimeoutExpired(["docker", "inspect"], 60)
        with patch.object(fixture.time, "sleep"), patch.object(fixture, "_docker", side_effect=[
                subprocess.TimeoutExpired(["docker", "create"], 60), missing, unavailable,
                cid + " run", cid + " run", ""]) as docker:
            with self.assertRaises(subprocess.TimeoutExpired):
                fixture._create("generated", "run", "synthetic")
            self.assertEqual(docker.call_args.args, ("rm", "-f", cid))

    def test_uncertain_cleanup_identifies_resource_without_deleting(self):
        timeout = subprocess.TimeoutExpired(["docker"], 60)
        with patch.object(fixture.time, "sleep"), patch.object(fixture, "_docker", side_effect=timeout) as docker:
            with self.assertRaisesRegex(RuntimeError, "cleanup unconfirmed.*generated.*run"):
                fixture._create("generated", "run", "synthetic")
            self.assertFalse(any(call.args[0] == "rm" for call in docker.call_args_list))

    def test_initialized_pair(self):
        if os.environ.get("HELLODOC_TEST_DISPOSABLE_PG") != "1":
            self.skipTest("Set HELLODOC_TEST_DISPOSABLE_PG=1 for two ephemeral PostgreSQL containers")
        with fixture.auth_fixture() as pair:
            java, go = pair["java"], pair["go"]
            self.assertNotEqual(java.port, go.port)
            self.assertNotEqual(java.directory, go.directory)
            for target in pair.values():
                self.assertGreater(target.user_id, 0)
                self.assertTrue(target.account_password)
                self.assertIn("sys_refresh_token|expires_at|timestamp|NO", target.catalog["columns"])
                self.assertIn("sys_user_auth|status|int2|YES", target.catalog["columns"])
                self.assertIn("sys_user|id|public.sys_user_id_seq", target.catalog["sequences"])
                self.assertTrue(any("fk_refresh_token_user" in item for item in target.catalog["foreign_keys"]))
                self.assertEqual([line.split("|")[0] for line in target.catalog["flyway"]],
                                 [str(i) for i in range(1, 9)])
                self.assertTrue(all(line.endswith("|t") for line in target.catalog["flyway"]))
                self.assertEqual(set(target.catalog), {"columns", "sequences", "foreign_keys", "flyway", "row_counts"})
                self.assertTrue(all(target.catalog.values()))
            expected = json.loads((fixture.ROOT / "tests/migration/fixtures/auth_postgres_catalog.json").read_text())
            self.assertEqual(java.catalog, expected)
            self.assertEqual(go.catalog, expected)
            self.verify_go_startup(go)
            self.assertEqual(fixture.catalog(go), expected)
        self.assertFalse(java.directory.exists())
        self.assertFalse(go.directory.exists())

    def verify_go_startup(self, target):
        exe = target.directory / "hellodoc"
        subprocess.run(["go", "build", "-o", str(exe), "./cmd/hellodoc"],
                       cwd=fixture.ROOT / "hellodoc-go", check=True)
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
        env = {"PATH": os.environ["PATH"], "HELLODOC_GO_ADDR": f"127.0.0.1:{port}",
               "DB_URL": f"jdbc:postgresql://127.0.0.1:{target.port}/{target.database}",
               "DB_USERNAME": target.username, "DB_PASSWORD": target.password,
               "JWT_SECRET": uuid.uuid4().hex + uuid.uuid4().hex}
        with subprocess.Popen([exe], cwd=target.directory, env=env) as server:
            try:
                opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
                for _ in range(100):
                    self.assertIsNone(server.poll(), "Go server exited during startup")
                    try:
                        with opener.open(f"http://127.0.0.1:{port}/api/system/info", timeout=1) as response:
                            self.assertEqual(response.status, 200)
                        break
                    except ConnectionError:
                        pass
                    except urllib.error.URLError as exc:
                        if not isinstance(exc.reason, ConnectionRefusedError):
                            raise
                    time.sleep(.1)
                else:
                    self.fail("Go startup timed out")
            finally:
                server.terminate()

    @unittest.skipUnless(os.environ.get("HELLODOC_TEST_DISPOSABLE_PG") == "1", "requires disposable Docker")
    def test_name_collision_preserves_existing_id(self):
        run = uuid.uuid4()
        owner = uuid.uuid4().hex
        name = f"hellodoc-auth-{run.hex}-java"
        existing = fixture._create(name, owner, uuid.uuid4().hex)
        try:
            with patch.object(fixture.uuid, "uuid4", return_value=run), self.assertRaises(subprocess.CalledProcessError):
                with fixture.auth_fixture():
                    pass
            self.assertEqual(fixture._docker("inspect", "--format", "{{.Id}}", name), existing)
        finally:
            fixture._remove(existing, owner)
