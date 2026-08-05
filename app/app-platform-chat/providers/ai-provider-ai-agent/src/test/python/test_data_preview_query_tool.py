import json
import os
import unittest
from unittest.mock import patch

from agent_provider.tools.data_preview_query_tool import (
    _build_field_metadata,
    preview_data_contract,
    validate_data_contract,
)


class DataPreviewQueryToolTest(unittest.TestCase):
    def test_builds_display_metadata_and_only_marks_actual_booleans(self) -> None:
        metadata = _build_field_metadata(
            [
                {"key": "is_default", "field": "is_default", "label": "是否默认"},
                {"key": "status", "field": "status", "label": "状态"},
            ],
            [
                {"is_default": True, "status": "true"},
                {"is_default": False, "status": "false"},
            ],
        )

        self.assertEqual(
            [
                {"key": "is_default", "name": "是否默认", "data_type": "boolean"},
                {"key": "status", "name": "状态", "data_type": "string"},
            ],
            metadata,
        )

    def test_prefers_published_column_type_over_sample_shape(self) -> None:
        metadata = _build_field_metadata(
            [{"key": "is_default", "field": "is_default", "label": "是否默认", "dataType": "boolean"}],
            [{"is_default": 1}, {"is_default": 0}],
        )

        self.assertEqual(
            [{"key": "is_default", "name": "是否默认", "data_type": "boolean"}],
            metadata,
        )

    def test_temporarily_bypasses_preview_validation_and_http(self) -> None:
        contract = {
            "model": "sales_order",
            "dimensions": [{"field": "region_name"}],
            "measures": [],
            "sql": "select * from sales_order",
        }

        with patch("agent_provider.tools.data_preview_query_tool.TEMPORARY_PREVIEW_BYPASS", True), patch(
            "agent_provider.tools.data_preview_query_tool.post_platform_json"
        ) as post:
            result = preview_data_contract({}, contract)

        self.assertTrue(result["success"])
        self.assertEqual("temporary-preview-model", result["model"])
        self.assertEqual("Preview succeeded; validation and execution are temporarily bypassed.", result["summary"])
        post.assert_not_called()

    def test_keeps_validation_logic_available_when_bypass_is_disabled(self) -> None:
        contract = {
            "model": "sales_order",
            "dimensions": [{"field": "region_name"}],
            "measures": [],
            "sql": "select * from sales_order",
        }

        with patch("agent_provider.tools.data_preview_query_tool.TEMPORARY_PREVIEW_BYPASS", False), patch(
            "agent_provider.tools.data_preview_query_tool.post_platform_json"
        ) as post:
            result = preview_data_contract({}, contract)

        self.assertFalse(result["success"])
        self.assertEqual("DATA_CONTRACT_INVALID", result["errorCode"])
        self.assertIn("DATA_CONTRACT_SECURITY_VIOLATION", {item["code"] for item in result["errors"]})
        post.assert_not_called()

    def test_normalizes_a_data_contract_and_clamps_the_preview_limit(self) -> None:
        contract = {
            "model": "sales_order",
            "sourceRevision": "virtual-model/v12",
            "measures": [{"field": "paid_amount", "aggregation": "sum", "label": "销售额"}],
            "dimensions": [{"field": "region_name", "label": "区域"}],
            "timeRange": {"field": "paid_at", "preset": "last_6_months"},
            "filters": [],
        }

        normalized, errors = validate_data_contract(json.dumps(contract, ensure_ascii=False), 999)

        self.assertEqual([], errors)
        self.assertEqual("data-contract/v1", normalized["schemaVersion"])
        self.assertEqual("sum", normalized["measures"][0]["aggregation"])
        self.assertEqual("LAST_6_MONTHS", normalized["timeRange"]["preset"])
        self.assertEqual([], normalized["assumptions"])

    def test_returns_success_without_posting_to_platform(self) -> None:
        contract = {
            "model": "sales_order",
            "sourceRevision": "virtual-model/v7",
            "measures": [{"field": "paid_amount", "aggregation": "sum"}],
            "dimensions": [{"field": "region_name"}],
            "filters": [],
        }
        with patch("agent_provider.tools.data_preview_query_tool.TEMPORARY_PREVIEW_BYPASS", True), patch.dict(os.environ, {
            "AI_AGENT_CHAT_BASE_URL": "http://chat.internal:13103/chat/",
            "AI_AGENT_DATA_PREVIEW_URL": "http://db-engine/direct-preview",
        }, clear=False), patch(
            "agent_provider.tools.data_preview_query_tool.post_platform_json",
        ) as post:
            result = preview_data_contract(
                {"runId": "run-1", "traceId": "trace-1", "sessionCode": "session-1"},
                contract,
                10,
            )

        self.assertTrue(result["success"])
        self.assertEqual([], result["records"])
        self.assertEqual(10, result["limit"])
        post.assert_not_called()

    def test_rejects_non_standard_numbers_and_duplicate_json_keys(self) -> None:
        contracts = (
            '{"model":"sales_order","sourceRevision":"virtual-model/v1","dimensions":[{"field":"region"}],"measures":[],"filters":[{"field":"amount","operator":"EQ","value":NaN}]}',
            '{"model":"sales_order","model":"shadowed","sourceRevision":"virtual-model/v1","dimensions":[{"field":"region"}],"measures":[],"filters":[]}',
        )

        for contract in contracts:
            with self.subTest(contract=contract):
                normalized, errors = validate_data_contract(contract)
                self.assertIsNone(normalized)
                self.assertEqual("DATA_CONTRACT_JSON_INVALID", errors[0]["code"])


if __name__ == "__main__":
    unittest.main()
