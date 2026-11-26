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
               "AUTH_STORE_PORT": str(target.port), "AUTH_STORE_PASSWORD": password}

        def check():
            subprocess.run(["go", "test", "-count=1", "-v", "./internal/auth", "-run", f"^{test}$"],
                           check=True, cwd=ROOT / "hellodoc-go", env=env)

        if test == "TestRegisterAtomic":
            sql("GRANT INSERT ON sys_user, sys_user_auth, sys_user_role TO store_reader; "
                "GRANT USAGE ON sys_user_id_seq, sys_user_auth_id_seq TO store_reader; "
                "INSERT INTO sys_role(role_code) VALUES ('common') ON CONFLICT DO NOTHING")
            for phase in ("initial", "roles", "updated", "deleted"):
                if phase == "roles":
                    sql("REVOKE INSERT ON sys_user_role FROM store_reader")
                if phase == "updated":
                    sql("GRANT INSERT ON sys_user_role TO store_reader; "
                        "UPDATE sys_role SET role_code='retired_user' WHERE role_code='user'")
                if phase == "deleted":
                    sql("UPDATE sys_role SET role_code='retired_common' WHERE role_code='common'")
                env["AUTH_LOOKUP_PHASE"] = phase
                check()
            return

        if test == "TestLoginPersistence":
            sql("GRANT INSERT, SELECT ON sys_refresh_token, sys_login_log TO store_reader; "
                "GRANT USAGE ON sys_refresh_token_id_seq, sys_login_log_id_seq TO store_reader")
            env["AUTH_ACCOUNT_PASSWORD"] = target.account_password
            env["AUTH_LOOKUP_PHASE"] = "initial"
            check()
            sql("REVOKE INSERT ON sys_login_log FROM store_reader")
            env["AUTH_LOOKUP_PHASE"] = "roles"
            check()
            sql("UPDATE sys_user SET status = 2 WHERE id = " + str(target.user_id))
            env["AUTH_LOOKUP_PHASE"] = "updated"
            check()
            return

        sql("""
            INSERT INTO sys_user(id, status) VALUES (-41, NULL);
            INSERT INTO sys_user_auth(id, user_id, identity_type, identifier, credential, status, verified)
              VALUES (-42, -41, 'PASSWORD', 'lookup_fixture', NULL, 1, false),
                     (-43, -41, 'EMAIL', 'lookup_fixture', 'different', 0, true),
                     (-44, -41, 'EMAIL', 'email_only', 'different', 0, true);
        """)
        if test == "TestBearerReloadAccount":
            sql("CREATE SCHEMA fixture_scope; GRANT USAGE ON SCHEMA fixture_scope TO store_reader")
            subprocess.run(["go", "test", "-count=1", "-v", "./cmd/hellodoc", "-run", "^TestBearerConfiguredSystemInfo$"],
                           check=True, cwd=ROOT / "hellodoc-go", env=env)
        env["AUTH_LOOKUP_PHASE"] = "initial"
        check()
        sql("""
            INSERT INTO sys_role(id, role_code, status) VALUES
                (-51, 'Zulu', 1), (-52, NULL, 0), (-53, 'null', 0),
                (-54, '😀', 0), (-55, 'Ａ', 0);
            INSERT INTO sys_user_role(user_id, role_id) VALUES
                (-41, -51), (-41, -52), (-41, -53), (-41, -54), (-41, -55);
        """)
        if test == "TestBearerReloadAccount":
            env["AUTH_LOOKUP_PHASE"] = "roles"
            check()
        sql("UPDATE sys_user SET status = 2, avatar = 'avatar.png', language_mode='en-US' WHERE id = -41")
        env["AUTH_LOOKUP_PHASE"] = "updated"
        check()
        if test == "TestBearerReloadAccount":
            sql("DELETE FROM sys_user_auth WHERE id = -42")
            env["AUTH_LOOKUP_PHASE"] = "deleted"
            check()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("test", nargs="?", choices=["TestLookupPasswordAccount", "TestBearerReloadAccount", "TestLoginPersistence", "TestRegisterAtomic"], default="TestLookupPasswordAccount")
    main(parser.parse_args().test)
