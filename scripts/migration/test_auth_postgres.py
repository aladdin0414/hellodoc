"""Run Go store integration tests against a freshly Java-initialized fixture."""
import argparse
import os
import secrets
import subprocess

from auth_fixture import ROOT, _docker, auth_fixture


def main(test):
    with auth_fixture() as targets:
        target = targets["go"]

        def sql(statement):
            return _docker("exec", target.container_id, "psql", "-XAtq", "-v", "ON_ERROR_STOP=1",
                           "-U", target.username, "-d", target.database, "-c", statement)

        password = secrets.token_hex(24)
        sql(f"CREATE ROLE store_reader LOGIN PASSWORD '{password}'; "
            "REVOKE TEMPORARY ON DATABASE auth_fixture FROM PUBLIC; "
            "GRANT SELECT ON sys_user, sys_user_auth, sys_role, sys_user_role TO store_reader")
        env = {"HOME": os.environ["HOME"], "PATH": os.environ["PATH"],
               "AUTH_STORE_FIXTURE": f"postgres://store_reader:{password}@127.0.0.1:{target.port}/auth_fixture?sslmode=disable"}

        def check():
            subprocess.run(["go", "test", "-count=1", "-v", "./internal/auth", "-run", f"^{test}$"],
                           check=True, cwd=ROOT / "hellodoc-go", env=env)

        sql("""
            INSERT INTO sys_user(id, status) VALUES (-41, NULL);
            INSERT INTO sys_user_auth(id, user_id, identity_type, identifier, credential, status, verified)
              VALUES (-42, -41, 'PASSWORD', 'lookup_fixture', NULL, 1, false),
                     (-43, -41, 'EMAIL', 'lookup_fixture', 'different', 0, true),
                     (-44, -41, 'EMAIL', 'email_only', 'different', 0, true);
        """)
        env["AUTH_LOOKUP_PHASE"] = "initial"
        check()
        sql("""
            UPDATE sys_user SET status = 2, avatar = 'avatar.png' WHERE id = -41;
            INSERT INTO sys_role(id, role_code, status) VALUES
                (-51, 'Zulu', 1), (-52, NULL, 0), (-53, 'null', 0),
                (-54, '😀', 0), (-55, 'Ａ', 0);
            INSERT INTO sys_user_role(user_id, role_id) VALUES
                (-41, -51), (-41, -52), (-41, -53), (-41, -54), (-41, -55);
        """)
        env["AUTH_LOOKUP_PHASE"] = "updated"
        check()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("test", nargs="?", choices=["TestLookupPasswordAccount"], default="TestLookupPasswordAccount")
    main(parser.parse_args().test)
