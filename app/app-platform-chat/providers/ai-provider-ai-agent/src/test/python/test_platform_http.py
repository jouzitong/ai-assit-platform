import os
import unittest
from unittest.mock import patch

from agent_provider.tools.platform_http import post_platform_json


class _JsonResponse:
    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        return False

    def read(self, size: int = -1) -> bytes:
        return b"{}"


class PlatformHttpTest(unittest.TestCase):
    def test_builds_authentication_and_agent_context_headers(self) -> None:
        with patch.dict(os.environ, {"AI_AGENT_PLATFORM_TOKEN": "temporary-token"}, clear=True), patch(
            "agent_provider.tools.platform_http.request.urlopen",
            return_value=_JsonResponse(),
        ) as urlopen:
            result = post_platform_json(
                "http://chat.internal/internal/tool",
                {"query": "value"},
                token_env_keys=("AI_AGENT_PLATFORM_TOKEN",),
                trace_id=" trace-1 ",
                run_id=" run-1 ",
                session_code=" session-1 ",
            )

        self.assertTrue(result["success"])
        outbound_request = urlopen.call_args.args[0]
        headers = dict(outbound_request.header_items())
        self.assertEqual("Bearer temporary-token", headers["Authorization"])
        self.assertEqual("trace-1", headers["X-trace-id"])
        self.assertEqual("run-1", headers["X-agent-run-id"])
        self.assertEqual("session-1", headers["X-session-code"])


if __name__ == "__main__":
    unittest.main()
