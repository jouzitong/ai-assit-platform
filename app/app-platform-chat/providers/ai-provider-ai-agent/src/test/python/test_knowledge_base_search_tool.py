import os
import unittest
from unittest.mock import patch

from agent_provider.tools.knowledge_base_search_tool import (
    _kb_search_url,
    _request_kb_search,
    available_knowledge_bases,
    build_knowledge_base_search_tool,
)


class KnowledgeBaseSearchToolTest(unittest.TestCase):
    def test_uses_the_chat_base_instead_of_a_legacy_full_url(self) -> None:
        with patch.dict(os.environ, {
            "AI_AGENT_CHAT_BASE_URL": "http://chat.internal:13103/chat/",
            "AI_AGENT_KB_SEARCH_URL": "http://gateway/legacy-kb-search",
        }, clear=True):
            self.assertEqual(
                "http://chat.internal:13103/chat/api/v1/ai/execution/kb/search",
                _kb_search_url(),
            )

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
            {
                "runId": "run-1",
                "traceId": "trace-1",
                "sessionCode": "session-1",
                "context": {"knowledgeBases": [{"kbCode": "product"}]},
            },
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
        self.assertEqual("trace-1", search.call_args.kwargs["trace_id"])
        self.assertEqual("run-1", search.call_args.kwargs["run_id"])
        self.assertEqual("session-1", search.call_args.kwargs["session_code"])

    def test_uses_the_shared_authenticated_platform_http_client(self) -> None:
        with patch(
            "agent_provider.tools.knowledge_base_search_tool.post_platform_json",
            return_value={
                "success": True,
                "data": {
                    "kbCode": "product",
                    "items": [{"documentId": "doc-1", "content": "content"}],
                },
            },
        ) as post:
            result = _request_kb_search(
                {"kbCode": "product", "query": "query", "topK": 3, "meta": {}},
                trace_id="trace-1",
                run_id="run-1",
                session_code="session-1",
            )

        self.assertTrue(result["success"])
        self.assertEqual("doc-1", result["items"][0]["documentId"])
        self.assertEqual(
            ("AI_AGENT_KB_SEARCH_TOKEN", "AI_AGENT_PLATFORM_TOKEN"),
            post.call_args.kwargs["token_env_keys"],
        )
        self.assertEqual("trace-1", post.call_args.kwargs["trace_id"])
        self.assertEqual("run-1", post.call_args.kwargs["run_id"])
        self.assertEqual("session-1", post.call_args.kwargs["session_code"])


if __name__ == "__main__":
    unittest.main()
