"""Exercise the Go fixture entry point without starting PostgreSQL."""
import os
from pathlib import Path
import select
import socket
import subprocess
import threading
import unittest

ROOT = Path(__file__).resolve().parents[2]


class TestStoreFixtureIsolation(unittest.TestCase):
    def test_refuses_configuration_before_connecting(self):
        for scenario in ("legacy_dsn", "invalid_phase"):
            with self.subTest(scenario=scenario), socket.socket() as listener:
                listener.bind(("127.0.0.1", 0))
                listener.listen()
                port = listener.getsockname()[1]
                env = {"HOME": os.environ["HOME"], "PATH": os.environ["PATH"],
                       "AUTH_STORE_PORT": str(port), "AUTH_STORE_PASSWORD": "ab" * 24,
                       "AUTH_LOOKUP_PHASE": "initial"}
                if scenario == "legacy_dsn":
                    env["AUTH_STORE_FIXTURE"] = f"postgres://fake@127.0.0.1:{port}/unused?sslmode=disable"
                    expected = "raw fixture DSN is unsupported"
                else:
                    env["AUTH_LOOKUP_PHASE"] = "other"
                    expected = "invalid fixture phase"

                connected = threading.Event()
                done = threading.Event()

                def observe():
                    while not done.is_set():
                        if select.select([listener], [], [], 0.05)[0]:
                            conn, _ = listener.accept()
                            conn.close()
                            connected.set()

                observer = threading.Thread(target=observe)
                observer.start()
                try:
                    run = subprocess.run(["go", "test", "-count=1", "./internal/auth",
                                          "-run", "^TestLookupPasswordAccount$"],
                                         cwd=ROOT / "hellodoc-go", env=env, capture_output=True,
                                         text=True, timeout=60)
                finally:
                    done.set()
                    observer.join()
                self.assertFalse(connected.is_set(), "Go connected before rejecting fixture configuration")
                self.assertNotEqual(run.returncode, 0)
                self.assertIn(expected, run.stdout)
