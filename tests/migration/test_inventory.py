import csv
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FIELDS = "id kind contract server_ref caller_ref test_ref status gap".split()


class InventoryTest(unittest.TestCase):
    def test_inventory(self):
        with (ROOT / "docs/migration/contracts.tsv").open(encoding="utf-8", newline="") as stream:
            reader = csv.DictReader(stream, delimiter="\t")
            self.assertEqual(reader.fieldnames, FIELDS)
            rows = list(reader)

        sources = {}
        ids = set()
        kinds = set()
        for row in rows:
            with self.subTest(contract=row["id"]):
                self.assertEqual(set(row), set(FIELDS))
                self.assertTrue(all(value and value.strip() for value in row.values()))
                self.assertNotIn(row["id"], ids)
                ids.add(row["id"])
                kinds.add(row["kind"])
                self.assertIn(row["status"], {"identified", "baseline", "verified"})
                if row["kind"] == "http":
                    self.assertRegex(row["id"], r"^(GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS) /\S*$")
                if row["status"] == "verified":
                    self.assertNotEqual(row["test_ref"], "-")
                self.assertNotEqual(row["server_ref"], "-")
                for field in ("server_ref", "caller_ref", "test_ref"):
                    if row[field] == "-":
                        self.assertNotEqual(row["gap"], "-")
                        continue
                    for ref in row[field].split("|"):
                        path, marker, symbol = ref.partition("#")
                        self.assertTrue(marker and symbol.strip(), ref)
                        self.assertFalse(Path(path).is_absolute(), ref)
                        self.assertNotIn("..", Path(path).parts, ref)
                        self.assertTrue((ROOT / path).is_file(), ref)
                        if path not in sources:
                            sources[path] = (ROOT / path).read_text(encoding="utf-8")
                        self.assertIn(symbol, sources[path], ref)
        self.assertEqual(kinds, set("http ws sse job database integration client mcp".split()))
        self.assertIn("GET /api/system/info", ids)
        for kind, sample in [("ws", "/ws/doc/{docId}"), ("client", "/api/auth/refresh-token")]:
            self.assertTrue(any(row["kind"] == kind and sample in row["contract"] for row in rows))

    def test_all_controller_mappings_and_mcp_tools_are_listed(self):
        with (ROOT / "docs/migration/contracts.tsv").open(encoding="utf-8", newline="") as stream:
            ids = {row["id"] for row in csv.DictReader(stream, delimiter="\t")}

        java = ROOT / "hellodoc-server/src/main/java"
        mapping = re.compile(r"@(Get|Post|Put|Delete|Patch)Mapping\b(?:\(([^)]*)\))?")
        for controller in java.rglob("*Controller.java"):
            source = controller.read_text(encoding="utf-8")
            prefix = re.search(r'@RequestMapping\("([^"]+)"\)', source)
            base = prefix[1] if prefix else ""
            for annotation in mapping.finditer(source):
                paths = re.findall(r'"([^"]+)"', annotation[2] or "") or [""]
                for path in paths:
                    endpoint = f"{annotation[1].upper()} {base}{path}"
                    self.assertIn(endpoint, ids, f"{controller.name} exposes {endpoint}")

        registration = next(java.rglob("McpConfiguration.java")).read_text(encoding="utf-8")
        names = re.findall(r'Tool\.builder\(\)\.name\("(\w+)"\)', registration)
        self.assertTrue(names)
        self.assertEqual({"tool " + name for name in names}, {i for i in ids if i.startswith("tool ")})
