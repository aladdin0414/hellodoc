import os
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
            self.assertEqual(java.catalog, go.catalog)
        self.assertFalse(java.directory.exists())
        self.assertFalse(go.directory.exists())

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
