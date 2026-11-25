import json
import subprocess
import sys
import threading
import time
import unittest
from datetime import datetime, timedelta
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[2] / "scripts/migration/compare_system_info.py"
VERSION = "1.2.3"
TIME_FORMAT = "%Y-%m-%d %H:%M:%S"


def system_info(**overrides):
    return {
        "name": "HelloDoc Server",
        "status": "UP",
        "version": VERSION,
        "serverTime": datetime.now().strftime(TIME_FORMAT),
        **overrides,
    }


class CompareSystemInfoTests(unittest.TestCase):
    def listen(self, payload=system_info, *, status=200, media_type="application/json",
               location=None):
        requests = []

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                requests.append((self.command, self.path))
                value = payload()
                body = value if isinstance(value, bytes) else json.dumps(value).encode()
                self.send_response(status)
                self.send_header("Content-Type", media_type)
                self.send_header("Content-Length", str(len(body)))
                if location:
                    self.send_header("Location", location)
                self.end_headers()
                try:
                    self.wfile.write(body)
                except (BrokenPipeError, ConnectionResetError):
                    pass

            def log_message(self, *args):
                pass

        server = HTTPServer(("127.0.0.1", 0), Handler)
        worker = threading.Thread(target=server.serve_forever, kwargs={"poll_interval": 0.01})
        worker.start()
        self.addCleanup(server.server_close)
        self.addCleanup(worker.join)
        self.addCleanup(server.shutdown)
        return server.server_port, requests

    def run_comparison(self, java_port, go_port):
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--java-port", str(java_port),
             "--go-port", str(go_port), "--version", VERSION],
            capture_output=True, text=True, timeout=10, check=False,
        )

    def assert_difference(self, result, *values):
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertNotIn("PASS", result.stdout + result.stderr)
        for value in ("Go", *values):
            self.assertIn(value, result.stdout + result.stderr)

    def test_requests_the_same_fixed_endpoint_on_both_ports(self):
        java_port, java_requests = self.listen()
        go_port, go_requests = self.listen()
        result = self.run_comparison(java_port, go_port)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(java_requests, [("GET", "/api/system/info")])
        self.assertEqual(java_requests, go_requests)

    def test_ignores_json_key_order_and_accepts_media_type_parameters(self):
        java_port, _ = self.listen()
        go_port, _ = self.listen(
            lambda: dict(reversed(list(system_info().items()))),
            media_type="application/json; charset=utf-8",
        )
        result = self.run_comparison(java_port, go_port)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_validates_each_response_time_across_a_second_boundary(self):
        times = []

        def java_response():
            info = system_info()
            times.append(info["serverTime"])
            return info

        def go_response():
            info = system_info()
            while info["serverTime"] == times[0]:
                time.sleep(0.01)
                info = system_info()
            times.append(info["serverTime"])
            return info

        java_port, _ = self.listen(java_response)
        go_port, _ = self.listen(go_response)
        result = self.run_comparison(java_port, go_port)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(len(times), 2)
        self.assertNotEqual(*times)

    def test_rejects_extra_field(self):
        java_port, _ = self.listen()
        go_port, _ = self.listen(lambda: system_info(extra="unexpected"))
        self.assert_difference(self.run_comparison(java_port, go_port),
                               "extra", "expected", "unexpected")

    def test_rejects_missing_field(self):
        def without_name():
            info = system_info()
            del info["name"]
            return info

        java_port, _ = self.listen()
        go_port, _ = self.listen(without_name)
        self.assert_difference(self.run_comparison(java_port, go_port),
                               "name", "expected", "missing")

    def test_rejects_non_string_fields(self):
        java_port, _ = self.listen()
        go_port, _ = self.listen(lambda: system_info(status=True))
        self.assert_difference(self.run_comparison(java_port, go_port),
                               "status", "string", "True")

    def test_rejects_wrong_content_type(self):
        java_port, _ = self.listen()
        go_port, _ = self.listen(media_type="text/plain")
        self.assert_difference(self.run_comparison(java_port, go_port),
                               "Content-Type", "application/json", "text/plain")

    def test_rejects_non_200_http_status(self):
        java_port, _ = self.listen()
        go_port, _ = self.listen(status=503)
        self.assert_difference(self.run_comparison(java_port, go_port), "HTTP", "200", "503")

    def test_rejects_version_drift(self):
        java_port, _ = self.listen()
        go_port, _ = self.listen(lambda: system_info(version="9.9.9"))
        self.assert_difference(self.run_comparison(java_port, go_port),
                               "version", VERSION, "9.9.9")

    def test_rejects_invalid_json(self):
        java_port, _ = self.listen()
        go_port, _ = self.listen(lambda: b"{")
        self.assert_difference(self.run_comparison(java_port, go_port), "JSON", "expected")

    def test_rejects_duplicate_fields(self):
        def duplicate_name():
            body = json.dumps(system_info())
            return (body[:-1] + ', "name": "HelloDoc Server"}').encode()

        java_port, _ = self.listen()
        go_port, _ = self.listen(duplicate_name)
        self.assert_difference(self.run_comparison(java_port, go_port), "name", "duplicate")

    def test_rejects_invalid_date_or_format(self):
        java_port, _ = self.listen()
        for timestamp in ("2025-02-29 10:00:00", "2025-1-01 10:00:00"):
            with self.subTest(timestamp=timestamp):
                go_port, _ = self.listen(lambda: system_info(serverTime=timestamp))
                self.assert_difference(self.run_comparison(java_port, go_port),
                                       "serverTime", timestamp)

    def test_rejects_stale_time(self):
        timestamp = (datetime.now() - timedelta(seconds=30)).strftime(TIME_FORMAT)
        java_port, _ = self.listen()
        go_port, _ = self.listen(lambda: system_info(serverTime=timestamp))
        self.assert_difference(self.run_comparison(java_port, go_port),
                               "serverTime", timestamp)

    def test_connection_timeout_is_an_error(self):
        release = threading.Event()
        java_port, _ = self.listen()
        go_port, requests = self.listen(lambda: release.wait(8) and system_info())
        self.addCleanup(release.set)
        try:
            result = self.run_comparison(java_port, go_port)
        finally:
            release.set()
        output = result.stdout + result.stderr
        self.assertEqual(result.returncode, 2, output)
        self.assertIn("Go", output)
        self.assertIn("timed out", output)
        self.assertNotIn("PASS", output)
        self.assertEqual(requests, [("GET", "/api/system/info")])

    def test_rejects_invalid_or_repeated_ports_before_sending_requests(self):
        java_port, requests = self.listen()
        for go_port in (java_port, 0, 65536, "localhost"):
            with self.subTest(go_port=go_port):
                result = self.run_comparison(java_port, go_port)
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertIn("--go-port", result.stderr)
                self.assertNotIn("PASS", result.stdout + result.stderr)
        self.assertEqual(requests, [])

    def test_does_not_follow_redirects(self):
        redirect_port, redirected_requests = self.listen()
        java_port, _ = self.listen()
        go_port, _ = self.listen(
            status=302, location=f"http://127.0.0.1:{redirect_port}/api/system/info",
        )
        self.assert_difference(self.run_comparison(java_port, go_port), "HTTP", "200", "302")
        self.assertEqual(len(redirected_requests), 0)
