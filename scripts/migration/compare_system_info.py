#!/usr/bin/env python3
"""Compare the Java and Go system info contracts on IPv4 loopback."""

import argparse
import http.client
import json
import re
import sys
from datetime import datetime

from system_info_http import HEADER_NAMES, INFO, SCENARIOS, SECURITY, normalize

HOST = "127.0.0.1"
PATH = "/api/system/info"
TIME_FORMAT = "%Y-%m-%d %H:%M:%S"
TIME_PATTERN = r"[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}"


class ContractDifference(ValueError):
    def __init__(self, field, expected, actual):
        super().__init__(f"{field}: expected {expected}, got {actual}")


def unique_object(pairs):
    result = {}
    for field, value in pairs:
        if field in result:
            raise ContractDifference(field, "one occurrence", f"duplicate value {value!r}")
        result[field] = value
    return result


def fetch(port):
    connection = http.client.HTTPConnection(HOST, port, timeout=5)
    try:
        started = datetime.now().replace(microsecond=0)
        connection.request("GET", PATH)
        with connection.getresponse() as response:
            if response.status != 200:
                raise ContractDifference("HTTP status", "200", response.status)
            content_type = response.getheader("Content-Type", "")
            if content_type.partition(";")[0].strip().lower() != "application/json":
                raise ContractDifference("Content-Type", "application/json", repr(content_type))
            body = response.read()
            finished = datetime.now().replace(microsecond=0)
    finally:
        connection.close()
    return body, started, finished


def validate(body, started, finished, version):
    try:
        info = json.loads(body, object_pairs_hook=unique_object)
    except RecursionError as error:
        raise ContractDifference(
            "JSON", "an object within the decoder's nesting limit", "excessive nesting"
        ) from error
    except (json.JSONDecodeError, UnicodeDecodeError) as error:
        raise ContractDifference("JSON", "a valid object", repr(body)) from error

    if not isinstance(info, dict):
        raise ContractDifference("JSON", "an object", repr(info))

    expected = {
        "name": "HelloDoc Server",
        "status": "UP",
        "version": version,
    }
    fields = expected.keys() | {"serverTime"}
    for field in sorted(info.keys() - fields):
        raise ContractDifference(field, "no extra field", repr(info[field]))
    for field in sorted(fields):
        if field not in info:
            raise ContractDifference(field, "a string field", "missing")
        if not isinstance(info[field], str):
            raise ContractDifference(field, "a string", repr(info[field]))
    for field, value in expected.items():
        if info[field] != value:
            raise ContractDifference(field, repr(value), repr(info[field]))

    timestamp = info["serverTime"]
    try:
        if not re.fullmatch(TIME_PATTERN, timestamp):
            raise ValueError("invalid timestamp format")
        server_time = datetime.strptime(timestamp, TIME_FORMAT)
    except ValueError as error:
        raise ContractDifference(
            "serverTime", "a valid date and time in YYYY-MM-DD HH:MM:SS format", repr(timestamp)
        ) from error

    if not started <= server_time <= finished:
        window = f"local time from {started.isoformat(sep=' ')} to {finished.isoformat(sep=' ')}"
        raise ContractDifference("serverTime", window, repr(timestamp))


def verify_http_matrix(port, version):
    for name, method, request_headers, status, headers, expected_body in SCENARIOS:
        connection = http.client.HTTPConnection(HOST, port, timeout=5)
        try:
            started = datetime.now().replace(microsecond=0)
            connection.request(method, PATH, headers=request_headers)
            with connection.getresponse() as response:
                body = response.read()
                finished = datetime.now().replace(microsecond=0)
                if response.status != status:
                    raise ContractDifference(f"{name} HTTP status", status, response.status)
                expected_headers = SECURITY | headers
                for header in sorted(HEADER_NAMES):
                    actual = normalize(header, response.getheader(header))
                    expected = normalize(header, expected_headers.get(header))
                    if actual != expected:
                        raise ContractDifference(f"{name} {header}", expected, actual)
            if expected_body is INFO:
                validate(body, started, finished, version)
            elif isinstance(expected_body, bytes):
                if body != expected_body:
                    raise ContractDifference(f"{name} body", expected_body, body)
            else:
                try:
                    actual = json.loads(body, object_pairs_hook=unique_object)
                except (ValueError, RecursionError) as error:
                    raise ContractDifference(f"{name} JSON", expected_body, str(error)) from error
                # Python equality conflates false with zero. Compare typed JSON values.
                if json.dumps(actual, sort_keys=True) != json.dumps(expected_body, sort_keys=True):
                    raise ContractDifference(f"{name} JSON", expected_body, actual)
        finally:
            connection.close()


def port_number(value):
    port = int(value)
    if not 1 <= port <= 65535:
        raise argparse.ArgumentTypeError("port must be between 1 and 65535")
    return port


def main():
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument("--java-port", type=port_number, required=True)
    parser.add_argument("--go-port", type=port_number, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--http-matrix", action="store_true",
                        help="check all HTTP scenarios with explicit CORS origin http://localhost:13000")
    args = parser.parse_args()
    if args.java_port == args.go_port:
        parser.error("--java-port and --go-port must differ")
    if not args.version.strip():
        parser.error("--version must be nonempty")

    result = 0
    for target, port in (("Java", args.java_port), ("Go", args.go_port)):
        try:
            if args.http_matrix:
                verify_http_matrix(port, args.version)
            else:
                body, started, finished = fetch(port)
                validate(body, started, finished, args.version)
        except ContractDifference as error:
            print(f"{target} ({HOST}:{port}): {error}", file=sys.stderr)
            result = max(result, 1)
        except (OSError, http.client.HTTPException) as error:
            print(f"{target} ({HOST}:{port}): request failed: {error}", file=sys.stderr)
            result = 2

    if result == 0:
        print("PASS: Java and Go system info match.")
    return result


if __name__ == "__main__":
    sys.exit(main())
