import json
import os
import unittest
from unittest.mock import patch

from agent_provider.tools.data_preview_query_tool import (
    preview_data_contract,
    validate_data_contract,
)


class DataPreviewQueryToolTest(unittest.TestCase):
    def test_rejects_physical_sql_and_endpoint_fields_before_http(self) -> None:
        contract = {
            "model": "sales_order",
            "dimensions": [{"field": "region_name"}],
            "measures": [],
            "sql": "select * from sales_order",
        }

        with patch("agent_provider.tools.data_preview_query_tool.post_platform_json") as post:
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

    def test_posts_only_the_normalized_contract_with_run_trace_context(self) -> None:
        contract = {
            "model": "sales_order",
            "sourceRevision": "virtual-model/v7",
            "measures": [{"field": "paid_amount", "aggregation": "sum"}],
            "dimensions": [{"field": "region_name"}],
            "filters": [],
        }
        response = {
            "success": True,
            "data": {
                "model": "sales_order",
                "catalogVersion": 7,
                "sourceRevision": "virtual-model/v7",
                "queryType": "AGGREGATE",
                "columns": ["region_name", "sum_paid_amount"],
                "records": [{"region_name": "华东", "sum_paid_amount": 12}],
                "total": 1,
            },
        }

        with patch.dict(os.environ, {
            "AI_AGENT_CHAT_BASE_URL": "http://chat.internal:13103/chat/",
            "AI_AGENT_DATA_PREVIEW_URL": "http://db-engine/direct-preview",
        }, clear=False), patch(
            "agent_provider.tools.data_preview_query_tool.post_platform_json",
            return_value=response,
        ) as post:
            result = preview_data_contract({"runId": "run-1", "traceId": "trace-1"}, contract, 10)

        self.assertTrue(result["success"])
        self.assertEqual(1, len(result["records"]))
        self.assertEqual(
            "http://chat.internal:13103/chat/internal/v1/ai/agent-tools/data-preview/query",
            post.call_args.args[0],
        )
        self.assertEqual("sales_order", post.call_args.args[1]["model"])
        self.assertEqual("trace-1", post.call_args.kwargs["trace_id"])
        self.assertEqual("run-1", post.call_args.kwargs["run_id"])
        self.assertEqual(10, post.call_args.args[1]["limit"])

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
