import json
import unittest

from agents import function_tool

from agent_provider.tools.render_json_validate_tool import (
    build_render_json_validate_tool,
    generate_render_json_for_run,
    validate_render_json_for_run,
)
from agent_provider.tools.render_validation import validate_render_document


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

        result = validate_render_document(json.dumps(document, ensure_ascii=False))

        self.assertTrue(result["valid"])
        self.assertTrue(result["documentHash"].startswith("sha256:"))
        self.assertEqual("render-validator/1.0.0", result["ruleVersion"])
        self.assertEqual("skill://render-json-generation/v1", result["catalogRevision"])

        tool_result = validate_render_json_for_run({}, document)
        self.assertTrue(tool_result["valid"])
        self.assertEqual(document, tool_result["renderDocument"])

    def test_function_tool_uses_a_strict_typed_fixture_schema(self) -> None:
        tool = build_render_json_validate_tool({}, function_tool)

        schema = tool.params_json_schema
        self.assertTrue(tool.strict_json_schema)
        self.assertEqual("object", schema["type"])
        self.assertEqual(["component_test_case", "datasource"], schema["required"])
        self.assertNotIn("render_document", schema["properties"])
        self.assertEqual(
            ["combo-chart", "form-edit", "line-chart", "list-table", "radar-chart"],
            sorted(schema["properties"]["component_test_case"]["enum"]),
        )
        self.assertEqual(
            "object",
            schema["$defs"]["RenderDatasourceInput"]["type"],
        )
        self.assertEqual(
            "array",
            schema["$defs"]["RenderDatasourceInput"]["properties"]["fields"]["type"],
        )

    def test_materializes_the_user_address_list_from_datasource_facts(self) -> None:
        result = generate_render_json_for_run(
            {},
            "list-table",
            {
                "key": "address-query",
                "model": "ods_trade_account_user_address",
                "fields": [
                    "id",
                    "userId",
                    "addressType",
                    "city",
                    "detailAddress",
                    "isDefault",
                ],
                "filters": [],
                "sorts": [],
                "page": 1,
                "page_size": 10,
            },
        )

        self.assertTrue(result["valid"], result["errors"])
        document = result["renderDocument"]
        root = document["root"]
        self.assertEqual("zg-list-main-layout", root["component"])
        self.assertEqual("ods_trade_account_user_address", root["datasource"]["model"])
        self.assertEqual(
            ["id", "userId", "addressType", "city", "detailAddress", "isDefault"],
            [field["key"] for field in root["props"]["schema"]["fields"]],
        )
        self.assertEqual(result["documentHash"], validate_render_document(document)["documentHash"])

    def test_rejects_a_filter_or_sort_field_not_projected_by_the_datasource(self) -> None:
        source = {
            "key": "address-query",
            "model": "ods_trade_account_user_address",
            "fields": ["id"],
            "filters": [{"field": "city", "operator": "eq", "value": "Shanghai"}],
            "sorts": [],
            "page": 1,
            "page_size": 10,
        }

        result = generate_render_json_for_run({}, "list-table", source)

        self.assertFalse(result["valid"])
        self.assertEqual("DATASOURCE_CONFIGURATION_INVALID", result["errors"][0]["code"])
        self.assertIn("Filter field is not in datasource.fields", result["errors"][0]["message"])

    def test_materializes_and_validates_each_frozen_component_case(self) -> None:
        cases = {
            "list-table": ["id"],
            "form-edit": ["id"],
            "line-chart": ["month", "count"],
            "combo-chart": ["month", "orders", "amount"],
            "radar-chart": ["stability", "performance", "security"],
        }

        for case_id, fields in cases.items():
            with self.subTest(case_id=case_id):
                result = generate_render_json_for_run(
                    {},
                    case_id,
                    {
                        "key": "fixture-source",
                        "model": "fixture_model",
                        "fields": fields,
                        "filters": [],
                        "sorts": [],
                        "page": 1,
                        "page_size": 10,
                    },
                )

                self.assertTrue(result["valid"], result["errors"])
                self.assertEqual(case_id, result["componentTestCase"])
                self.assertIn("renderDocument", result)

    def test_materializes_filters_as_an_implicit_and_without_filter_expr(self) -> None:
        result = generate_render_json_for_run(
            {},
            "list-table",
            {
                "key": "address-query",
                "model": "ods_trade_account_user_address",
                "fields": ["id", "city", "isDefault"],
                "filters": [
                    {"field": "city", "operator": "eq", "value": "Shanghai"},
                    {"field": "isDefault", "operator": "eq", "value": True},
                ],
                "sorts": [{"field": "id", "direction": "desc"}],
                "page": 1,
                "page_size": 10,
            },
        )

        self.assertTrue(result["valid"], result["errors"])
        datasource = result["renderDocument"]["root"]["datasource"]
        self.assertEqual({"city": "Shanghai", "isDefault": True}, datasource["filter_dict"])
        self.assertNotIn("filterExpr", datasource)
        self.assertEqual([{"field": "id", "order": "desc"}], datasource["ext"]["sorts"])

    def test_reports_duplicate_ids_and_missing_component_versions(self) -> None:
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

        result = validate_render_document(document)
        codes = {item["code"] for item in result["errors"]}

        self.assertFalse(result["valid"])
        self.assertIn("DUPLICATE_NODE_ID", codes)
        self.assertIn("COMPONENT_VERSION_REQUIRED", codes)
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

        result = validate_render_document(document)
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
        result = validate_render_document(document)
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
        result = validate_render_document(document)
        self.assertFalse(result["valid"])
        self.assertTrue(any(item["code"] == "SECURITY_VIOLATION" for item in result["errors"]))

    def test_validates_without_an_online_component_catalog(self) -> None:
        document = {
            "protocol": "render-json",
            "protocolVersion": "1.0",
            "pageId": "page",
            "root": {
                "id": "root",
                "component": "line-chart-renderer",
                "componentVersion": "1.0.0",
                "props": {"categories": []},
            },
        }

        result = validate_render_document(document)

        self.assertTrue(result["valid"])
        self.assertEqual("skill://render-json-generation/v1", result["catalogRevision"])

    def test_rejects_non_standard_numbers_and_duplicate_json_keys(self) -> None:
        invalid_documents = (
            '{"protocol":"render-json","protocolVersion":"1.0","pageId":"p","root":{"id":"r","component":"line-chart-renderer","componentVersion":"1.0.0","props":{"categories":[],"smooth":NaN}}}',
            '{"protocol":"render-json","protocol":"shadowed","protocolVersion":"1.0","pageId":"p","root":{}}',
        )

        for document in invalid_documents:
            with self.subTest(document=document):
                result = validate_render_document(document)
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

        result = validate_render_document(document)

        self.assertFalse(result["valid"])
        self.assertTrue(any("filter value is too complex" in item["message"] for item in result["errors"]))

    def test_accepts_proof_bound_count_query(self) -> None:
        document = {
            "protocol": "render-json",
            "protocolVersion": "1.0",
            "pageId": "knowledge-summary",
            "root": {
                "id": "document-count",
                "component": "line-chart-renderer",
                "componentVersion": "1.0.0",
                "props": {"categories": []},
                "datasource": {
                    "key": "knowledge-document-count",
                    "type": "semantic-query",
                    "queryType": "count",
                    "model": "ai_kb_document",
                    "fields": ["id", "status", "is_delete"],
                    "measures": [
                        {"field": "id", "aggregation": "count", "alias": "document_count"}
                    ],
                    "filters": [
                        {"field": "status", "operator": "eq", "value": 1},
                        {"field": "is_delete", "operator": "eq", "value": 0},
                    ],
                    "contractRef": "data-contract/ai-kb-document/v1",
                    "previewProofRef": "data-preview/ai-kb-document-count/v1",
                },
            },
        }

        result = validate_render_document(document)

        self.assertTrue(result["valid"])

    def test_accepts_proof_bound_aggregate_query(self) -> None:
        document = {
            "protocol": "render-json",
            "protocolVersion": "1.0",
            "pageId": "sales-summary",
            "root": {
                "id": "region-paid-amount",
                "component": "line-chart-renderer",
                "componentVersion": "1.0.0",
                "props": {"categories": []},
                "datasource": {
                    "key": "sales-order-region-paid-amount",
                    "type": "semantic-query",
                    "queryType": "aggregate",
                    "model": "sales_order",
                    "fields": ["region_name", "paid_amount", "paid_at"],
                    "dimensions": ["region_name"],
                    "measures": [
                        {"field": "paid_amount", "aggregation": "sum", "alias": "paid_amount_sum"}
                    ],
                    "timeRange": {"field": "paid_at", "preset": "LAST_6_MONTHS"},
                    "sorts": [{"field": "paid_amount", "direction": "DESC"}],
                    "limit": 20,
                    "contractRef": "data-contract/sales-order/virtual-model-v12",
                    "previewProofRef": "data-preview/sales-order-region-paid-amount/virtual-model-v12",
                },
            },
        }

        result = validate_render_document(document)

        self.assertTrue(result["valid"])

    def test_rejects_invalid_count_query_shape(self) -> None:
        document = {
            "protocol": "render-json",
            "protocolVersion": "1.0",
            "pageId": "knowledge-summary",
            "root": {
                "id": "document-count",
                "component": "line-chart-renderer",
                "componentVersion": "1.0.0",
                "props": {"categories": []},
                "datasource": {
                    "key": "knowledge-document-count",
                    "type": "semantic-query",
                    "queryType": "count",
                    "model": "ai_kb_document",
                    "fields": ["id", "owner"],
                    "dimensions": ["owner"],
                    "measures": [{"field": "id", "aggregation": "sum"}],
                    "contractRef": "data-contract/ai-kb-document/v1",
                    "previewProofRef": "data-preview/ai-kb-document-count/v1",
                },
            },
        }

        result = validate_render_document(document)

        self.assertFalse(result["valid"])
        messages = {item["message"] for item in result["errors"]}
        self.assertIn("count query must not declare dimensions", messages)
        self.assertIn("count query requires exactly one measure using count aggregation", messages)


if __name__ == "__main__":
    unittest.main()
