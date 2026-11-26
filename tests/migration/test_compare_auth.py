"""Mutation tests for the live comparator's wire, JWT and persistence checks."""
from contextlib import redirect_stderr
from io import StringIO
from pathlib import Path
import sys
import time
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts/migration"))
import compare_auth as compare


class DriftTests(unittest.TestCase):
    def rejects(self, checker, *args):
        diagnostic = StringIO()
        with patch.object(compare, "main", side_effect=lambda: checker(*args)), redirect_stderr(diagnostic):
            self.assertNotEqual(compare.cli(), 0)
        self.assertIn("contract mismatch:", diagnostic.getvalue())
        self.assertNotIn("private-test-value", diagnostic.getvalue())

    def test_wrong_business_code_and_missing_null(self):
        expected = {"body": {"code": 1005, "message": "Rejected", "data": None}}
        for changed in ({"code": 0, "message": "Rejected", "data": None}, {"code": 1005, "message": "Rejected"}):
            with self.subTest(changed=changed):
                self.rejects(compare.validate_response, "refresh", expected, {"body": changed}, "", "")

    def test_wrong_type_and_extra_role(self):
        now = int(time.time())
        base = {"iat": now, "sub": "private-test-value"}
        access = {**base, "exp": now + 86400, "roles": ["ROLE_user", "ROLE_admin"]}
        self.rejects(compare.validate_claims, access, base["sub"], False)
        identifier = "00000000-0000-4000-8000-000000000001"
        refresh = {**base, "exp": now + 7 * 86400, "type": "access", "jti": identifier, "family": identifier}
        self.rejects(compare.validate_claims, refresh, base["sub"], True)

    def test_missing_audit_and_wrong_parent(self):
        self.rejects(compare.check, "login audit", "1", "0")
        self.rejects(compare.validate_session, "parent", "child", "private-test-value|child|ROTATED||0", False)
