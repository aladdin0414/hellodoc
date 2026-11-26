"""Run Go store integration tests against a freshly Java-initialized fixture."""
import argparse
import json
import os
import secrets
import subprocess
import urllib.request

from auth_fixture import ROOT, _docker, auth_fixture


def fixture_sql(target, statement):
    return _docker("exec", target.container_id, "psql", "-XAtq", "-v", "ON_ERROR_STOP=1",
                   "-U", target.username, "-d", target.database, "-c", statement)


def probe_java_refresh(target, base, opener):
    def post(path, body, expected):
        request = urllib.request.Request(f"{base}/api/auth/{path}", json.dumps(body).encode(),
                                         {"Content-Type": "application/json", "X-Language": "en"})
        with opener.open(request, timeout=15) as response:
            result = json.load(response)
            if response.status != 200 or result["code"] != expected:
                raise RuntimeError(f"Java {path} returned {response.status}/{result['code']}, expected 200/{expected}")
            return result

    def child_state():
        # Each psql call observes committed state in a new connection outside the HTTP transaction.
        return fixture_sql(target, """
            SELECT p.revoked_reason, c.revoked_at IS NULL, COALESCE(c.revoked_reason, '<null>')
            FROM sys_refresh_token p JOIN sys_refresh_token c ON c.parent_jti=p.jti
            WHERE p.parent_jti IS NULL AND p.replaced_by_jti=c.jti
        """)

    initial = post("login", {"username": "migration_fixture", "password": target.account_password}, 0)
    old = {"refreshToken": initial["data"]["refreshToken"]}
    wrong_case = post("refresh-token", {"REFRESHTOKEN": old["refreshToken"]}, 1005)
    if wrong_case["message"] != "Refresh token is invalid" or wrong_case["data"] is not None:
        raise RuntimeError("Java wrong-case refresh field response changed")
    fresh = post("refresh-token", old, 0)
    print("Java baseline: REFRESHTOKEN is ignored; the same ticket rotated with refreshToken.", flush=True)
    before = child_state()
    failure = post("refresh-token", old, 1005)
    after = child_state()
    if (before, after) != ("ROTATED|t|<null>", "ROTATED|t|<null>") or (
        failure["message"] != "Refresh token reuse detected" or failure["data"] is not None
    ):
        raise RuntimeError(f"Java refresh baseline changed: committed child state {before!r} -> {after!r}")
    post("refresh-token", {"refreshToken": fresh["data"]["refreshToken"]}, 0)
    print("Java baseline: reuse returned 1005; committed child stayed unrevoked and subsequently rotated.", flush=True)


def main(test):
    java_probe = probe_java_refresh if test == "TestRefreshRotationPostgres" else None
    with auth_fixture(java_probe=java_probe) as targets:
        target = targets["go"]

        def sql(statement):
            return fixture_sql(target, statement)

        password = secrets.token_hex(24)
        sql(f"CREATE ROLE store_reader LOGIN PASSWORD '{password}'; "
            "REVOKE TEMPORARY ON DATABASE auth_fixture FROM PUBLIC; "
            "GRANT SELECT ON sys_user, sys_user_auth, sys_role, sys_user_role TO store_reader")
        env = {"HOME": os.environ["HOME"], "PATH": os.environ["PATH"],
               "AUTH_STORE_PORT": str(target.port), "AUTH_STORE_PASSWORD": password}

        def check():
            race_flag = ["-race"] if test == "TestRefreshRotationPostgres" else []
            subprocess.run(["go", "test", *race_flag, "-count=1", "-v", "./internal/auth", "-run", f"^{test}$"],
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

        if test == "TestRefreshRotationPostgres":
            sql("""
                GRANT INSERT, SELECT, UPDATE ON sys_refresh_token TO store_reader;
                GRANT INSERT, SELECT ON sys_login_log TO store_reader;
                GRANT USAGE ON sys_refresh_token_id_seq, sys_login_log_id_seq TO store_reader;
                GRANT UPDATE (role_code) ON sys_role TO store_reader;
                GRANT UPDATE (status) ON sys_user TO store_reader;
                INSERT INTO sys_user(id, status) VALUES (-91, 0);
                INSERT INTO sys_user_auth(id, user_id, identity_type, identifier, status, verified)
                    VALUES (-92, -91, 'PASSWORD', 'refresh_other', 0, true);
                CREATE TABLE refresh_failures (family_id text PRIMARY KEY);
                GRANT SELECT, INSERT, DELETE ON refresh_failures TO store_reader;
                CREATE FUNCTION reject_fixture_child() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.parent_jti IS NOT NULL AND EXISTS (
                        SELECT 1 FROM refresh_failures WHERE family_id=NEW.family_id
                    ) THEN
                        RAISE EXCEPTION 'fixture child insert rejected';
                    END IF;
                    RETURN NEW;
                END;
                $$;
                CREATE TRIGGER reject_fixture_child BEFORE INSERT ON sys_refresh_token
                    FOR EACH ROW EXECUTE FUNCTION reject_fixture_child();
            """)
            env["AUTH_ACCOUNT_PASSWORD"] = target.account_password
            env["AUTH_LOOKUP_PHASE"] = "initial"
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
    parser.add_argument("test", nargs="?", choices=["TestLookupPasswordAccount", "TestBearerReloadAccount", "TestLoginPersistence", "TestRegisterAtomic", "TestRefreshRotationPostgres"], default="TestLookupPasswordAccount")
    main(parser.parse_args().test)
