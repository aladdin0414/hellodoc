import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts/migration"))
import compare_system_info as compare
import system_info_http as matrix


class HTTPMatrixTests(unittest.TestCase):
    def check_post(self, status=200, body=b'{"code":9999,"message":"bad","data":null}', extra=None):
        scenario = next(case for case in matrix.SCENARIOS if case[0] == "empty POST")
        response = MagicMock()
        response.status = status
        response.read.return_value = body
        response.getheader.side_effect = (matrix.SECURITY | matrix.JSON_HEADERS | (extra or {})).get
        with patch.object(compare, "SCENARIOS", [scenario]), patch.object(compare.http.client, "HTTPConnection") as conn:
            conn.return_value.getresponse.return_value.__enter__.return_value = response
            compare.verify_http_matrix(12345, "1")

    def test_http_status_checked_independently(self):
        with self.assertRaisesRegex(compare.ContractDifference, "HTTP status"):
            self.check_post(status=405)

    def test_error_payload_checked_with_types(self):
        for body in (b'{}', b'{"code":"9999"}', b'not json'):
            with self.subTest(body=body), self.assertRaisesRegex(compare.ContractDifference, "JSON"):
                self.check_post(body=body)

    def test_unexpected_cache_header_fails(self):
        with self.assertRaisesRegex(compare.ContractDifference, "Cache-Control"):
            self.check_post(extra={"Cache-Control": "public"})

    def test_header_method_order_ignored(self):
        self.assertEqual(matrix.normalize("Allow", "GET,HEAD"), matrix.normalize("Allow", "HEAD, GET"))
