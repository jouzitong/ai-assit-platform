import json
import unittest

from agent_provider.tools.render_validation import validate_render_document


def catalog_result(*keys: str):
    return {
        "success": True,
        "catalogRevision": "sha256:" + "c" * 64,
        "components": [
            {
                "key": key,
                "componentVersion": "1.0.0",
                "sourceRevision": "sha256:" + "d" * 64,
                "contractAvailable": True,
                "parameters": [
                    {"key": "categories", "type": "Array<string | number>", "required": True},
                    {"key": "smooth", "type": "boolean", "required": False},
                ],
                "events": [{"name": "reload"}],
            }
            for key in keys
        ],
    }


class RenderJsonValidateToolTest(unittest.TestCase):
    def test_accepts_a_complete_document_and_returns_validation_proof(self) -> None:
        document = {
            "protocol": "render-json",
            "protocolVersion": "1.0",
            "pageId": "sales-dashboard",
            "root": {
                "id": "trend",
                "component": "line-chart-renderer",
                "componentVersion": "1.0.0",
                "props": {"categories": ["1月", "2月"], "smooth": True},
                "events": [{"event": "reload", "actionRef": "RELOAD_DATA"}],
                "actions": [{"key": "RELOAD_DATA", "type": "refresh"}],
            },
        }

        result = validate_render_document(
            json.dumps(document, ensure_ascii=False),
            lambda keys: catalog_result(*keys),
        )

        self.assertTrue(result["valid"])
        self.assertTrue(result["documentHash"].startswith("sha256:"))
        self.assertEqual("render-validator/1.0.0", result["ruleVersion"])
        self.assertEqual("sha256:" + "c" * 64, result["catalogRevision"])

    def test_reports_duplicate_ids_unknown_props_and_missing_required_props(self) -> None:
        document = {
            "protocol": "render-json",
            "protocolVersion": "1.0",
            "pageId": "sales-dashboard",
            "root": {
                "id": "same",
                "component": "line-chart-renderer",
                "props": {"unknown": 1},
                "children": [{"id": "same", "component": "missing-renderer"}],
            },
        }

        result = validate_render_document(document, lambda keys: catalog_result("line-chart-renderer"))
        codes = {item["code"] for item in result["errors"]}

        self.assertFalse(result["valid"])
        self.assertIn("DUPLICATE_NODE_ID", codes)
        self.assertIn("PROP_NOT_ALLOWED", codes)
        self.assertIn("PROP_REQUIRED", codes)
        self.assertIn("RENDERER_NOT_FOUND", codes)
        self.assertTrue(all("jsonPath" in item and "recoverable" in item for item in result["errors"]))

    def test_rejects_dangerous_urls_sql_and_executable_strings(self) -> None:
        document = {
            "protocol": "render-json",
            "protocolVersion": "1.0",
            "pageId": "unsafe",
            "root": {
                "id": "root",
                "component": "line-chart-renderer",
                "props": {"categories": []},
                "datasource": {
                    "type": "db-query-list",
                    "url": "https://evil.invalid/query",
                    "sql": "select * from secret",
                },
                "actions": [{"key": "RUN", "handler": "=function(){ return 1 }"}],
            },
        }

        result = validate_render_document(document, lambda keys: catalog_result(*keys))
        violations = [item for item in result["errors"] if item["code"] == "SECURITY_VIOLATION"]

        self.assertFalse(result["valid"])
        self.assertGreaterEqual(len(violations), 3)

    def test_rejects_unknown_datasource_fields_unpinned_versions_and_missing_actions(self) -> None:
        document = {
            "protocol": "render-json",
            "protocolVersion": "1.0",
            "pageId": "unsafe",
            "root": {
                "id": "root",
                "component": "line-chart-renderer",
                "props": {"categories": []},
                "datasource": {"type": "static", "key": "source", "data": {}, "extra": 1},
                "events": [{"event": "reload", "actionRef": "MISSING"}],
            },
        }
        result = validate_render_document(document, lambda keys: catalog_result(*keys))
        codes = {item["code"] for item in result["errors"]}
        self.assertIn("SCHEMA_INVALID", codes)
        self.assertIn("COMPONENT_VERSION_REQUIRED", codes)
        self.assertIn("ACTION_REFERENCE_NOT_FOUND", codes)

    def test_rejects_non_http_url_schemes_in_declarative_props(self) -> None:
        document = {
            "protocol": "render-json",
            "protocolVersion": "1.0",
            "pageId": "unsafe",
            "root": {
                "id": "root",
                "component": "line-chart-renderer",
                "componentVersion": "1.0.0",
                "props": {"categories": ["ws://evil.invalid", "file:///etc/passwd"]},
            },
        }
        result = validate_render_document(document, lambda keys: catalog_result(*keys))
        self.assertFalse(result["valid"])
        self.assertTrue(any(item["code"] == "SECURITY_VIOLATION" for item in result["errors"]))

    def test_fails_closed_when_live_catalog_is_unavailable(self) -> None:
        document = {
            "protocol": "render-json",
            "protocolVersion": "1.0",
            "pageId": "page",
            "root": {"id": "root", "component": "line-chart-renderer", "props": {"categories": []}},
        }

        result = validate_render_document(document, lambda _keys: {"success": False, "error": "offline"})

        self.assertFalse(result["valid"])
        self.assertEqual("COMPONENT_CATALOG_UNAVAILABLE", result["errors"][-1]["code"])

    def test_rejects_non_standard_numbers_and_duplicate_json_keys(self) -> None:
        invalid_documents = (
            '{"protocol":"render-json","protocolVersion":"1.0","pageId":"p","root":{"id":"r","component":"line-chart-renderer","componentVersion":"1.0.0","props":{"categories":[],"smooth":NaN}}}',
            '{"protocol":"render-json","protocol":"shadowed","protocolVersion":"1.0","pageId":"p","root":{}}',
        )

        for document in invalid_documents:
            with self.subTest(document=document):
                result = validate_render_document(document, lambda keys: catalog_result(*keys))
                self.assertFalse(result["valid"])
                self.assertEqual("JSON_PARSE_FAILED", result["errors"][0]["code"])

    def test_rejects_oversized_scalar_filter_values(self) -> None:
        document = {
            "protocol": "render-json",
            "protocolVersion": "1.0",
            "pageId": "page",
            "root": {
                "id": "root",
                "component": "line-chart-renderer",
                "componentVersion": "1.0.0",
                "props": {"categories": []},
                "datasource": {
                    "key": "sales",
                    "type": "semantic-query",
                    "model": "sales_order",
                    "filter_dict": {"region": {"op": "eq", "value": "x" * 4097}},
                },
            },
        }

        result = validate_render_document(document, lambda keys: catalog_result(*keys))

        self.assertFalse(result["valid"])
        self.assertTrue(any("filter value is too complex" in item["message"] for item in result["errors"]))


if __name__ == "__main__":
    unittest.main()
