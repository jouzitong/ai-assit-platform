import os
import unittest
from unittest.mock import patch

from agent_provider.chat_endpoint import chat_base_url, chat_endpoint


class ChatEndpointTest(unittest.TestCase):
    def test_uses_the_local_chat_service_by_default(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            self.assertEqual("http://127.0.0.1:13103/chat", chat_base_url())
            self.assertEqual(
                "http://127.0.0.1:13103/chat/internal/v1/test",
                chat_endpoint("/internal/v1/test"),
            )

    def test_normalizes_a_configured_chat_base_url(self) -> None:
        with patch.dict(
            os.environ,
            {"AI_AGENT_CHAT_BASE_URL": "  http://chat.internal:13103/chat/  "},
            clear=True,
        ):
            self.assertEqual(
                "http://chat.internal:13103/chat/api/v1/test",
                chat_endpoint("/api/v1/test"),
            )

    def test_rejects_a_non_application_route(self) -> None:
        with self.assertRaisesRegex(ValueError, "absolute application route"):
            chat_endpoint("https://db-engine/internal/query")


if __name__ == "__main__":
    unittest.main()
