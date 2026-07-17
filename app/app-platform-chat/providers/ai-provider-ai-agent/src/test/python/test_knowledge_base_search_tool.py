import unittest
from unittest.mock import patch

from agent_provider.tools.knowledge_base_search_tool import (
    available_knowledge_bases,
    build_knowledge_base_search_tool,
)


class KnowledgeBaseSearchToolTest(unittest.TestCase):
    def test_filters_invalid_and_duplicate_runtime_catalog_entries(self) -> None:
        values = available_knowledge_bases({
            "context": {
                "knowledgeBases": [
                    {"kbCode": "product", "name": "Product"},
                    {"kbCode": " product ", "description": "Duplicate"},
                    {"kbCode": ""},
                    {"name": "Missing code"},
                ]
            }
        })

        self.assertEqual([{"kbCode": "product", "name": "Product"}], values)

    def test_rejects_a_kb_code_outside_the_runtime_allowlist(self) -> None:
        captured = {}

        def fake_function_tool(**kwargs):
            def decorator(function):
                captured["function"] = function
                return function
            return decorator

        tool = build_knowledge_base_search_tool(
            {"traceId": "trace-1", "context": {"knowledgeBases": [{"kbCode": "product"}]}},
            fake_function_tool,
        )

        self.assertIsNotNone(tool)
        result = captured["function"]("other", "how to configure")
        self.assertFalse(result["success"])
        self.assertEqual(["product"], result["availableKbCodes"])

    def test_uses_the_selected_allowed_kb_code_for_retrieval(self) -> None:
        captured = {}

        def fake_function_tool(**kwargs):
            def decorator(function):
                captured["function"] = function
                return function
            return decorator

        build_knowledge_base_search_tool(
            {"traceId": "trace-1", "context": {"knowledgeBases": [{"kbCode": "product"}]}},
            fake_function_tool,
        )
        with patch(
                "agent_provider.tools.knowledge_base_search_tool._request_kb_search",
                return_value={"success": True, "items": []},
        ) as search:
            result = captured["function"]("product", "how to configure", 3)

        self.assertTrue(result["success"])
        self.assertEqual("product", result["kbCode"])
        self.assertEqual("product", search.call_args.args[0]["kbCode"])
        self.assertEqual("trace-1", search.call_args.args[0]["meta"]["traceId"])


if __name__ == "__main__":
    unittest.main()
