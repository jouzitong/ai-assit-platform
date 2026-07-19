import json
import os
import unittest
from unittest.mock import patch

from agent_provider.gateway import _invoke_gateway


class _Response:
    def __init__(self, body: dict) -> None:
        self._body = json.dumps(body).encode("utf-8")

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def read(self) -> bytes:
        return self._body


class ToolGatewayTest(unittest.TestCase):
    def test_invokes_the_versioned_gateway_with_auth_and_idempotency(self) -> None:
        descriptor = {"code": "issue-create", "version": 4, "timeoutMs": 5000}
        run = {"runId": "run-1", "requestId": "request-1", "traceId": "trace-1"}
        captured = []

        def urlopen(http_request, timeout):
            captured.append((http_request, timeout))
            return _Response({
                "toolCode": "issue-create",
                "toolVersion": 4,
                "status": "SUCCESS",
                "output": {"id": "ISSUE-1"},
                "durationMs": 12,
            })

        with patch.dict(os.environ, {
            "AI_AGENT_CHAT_BASE_URL": "http://chat.internal:13103/chat/",
            "AI_AGENT_TOOL_GATEWAY_URL": "http://tool-gateway/legacy-base/",
            "AI_AGENT_TOOL_GATEWAY_TOKEN": "token-value",
            "AI_AGENT_TOOL_APPROVAL": "approved-grant",
        }, clear=False), patch("agent_provider.gateway.request.urlopen", urlopen):
            result = _invoke_gateway(descriptor, run, {"title": "Fix"}, "sha256:snapshot")

        http_request, timeout = captured[0]
        self.assertEqual(
            "http://chat.internal:13103/chat/api/v1/ai/tool-gateway/issue-create/versions/4/invoke",
            http_request.full_url,
        )
        self.assertEqual("Bearer token-value", http_request.get_header("Authorization"))
        self.assertTrue(http_request.get_header("Idempotency-key"))
        self.assertEqual("approved-grant", http_request.get_header("X-tool-approval"))
        self.assertEqual({"title": "Fix"}, json.loads(http_request.data)["arguments"])
        self.assertEqual("run-1", json.loads(http_request.data)["run"]["runId"])
        self.assertEqual("sha256:snapshot", json.loads(http_request.data)["run"]["snapshotHash"])
        self.assertEqual(5.0, timeout)
        self.assertEqual("SUCCESS", result["status"])

    def test_fails_closed_when_gateway_auth_is_missing(self) -> None:
        with patch.dict(os.environ, {"AI_AGENT_TOOL_GATEWAY_URL": "http://gateway"}, clear=True):
            result = _invoke_gateway(
                {"code": "issue-create", "version": 4, "timeoutMs": 5000},
                {"runId": "run-1"},
                {"title": "Fix"},
            )

        self.assertEqual("FAILED", result["status"])
        self.assertIn("TOKEN", result["error"])

    def test_fails_closed_when_the_frozen_snapshot_hash_is_missing(self) -> None:
        with patch.dict(os.environ, {
            "AI_AGENT_TOOL_GATEWAY_URL": "http://gateway",
            "AI_AGENT_TOOL_GATEWAY_TOKEN": "token-value",
        }, clear=True):
            result = _invoke_gateway(
                {"code": "issue-create", "version": 4, "timeoutMs": 5000},
                {"runId": "run-1"},
                {"title": "Fix"},
            )

        self.assertEqual("FAILED", result["status"])
        self.assertIn("snapshotHash", result["error"])


if __name__ == "__main__":
    unittest.main()
