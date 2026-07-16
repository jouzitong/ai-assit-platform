import unittest

from agent_provider.protocol import build_application_input, normalize_payload


class ProtocolTest(unittest.TestCase):
    def test_legacy_chat_payload_becomes_a_v2_agent_snapshot(self) -> None:
        payload = normalize_payload(
            {
                "model": "gpt-test",
                "messages": [
                    {"role": "SYSTEM", "content": "Be exact."},
                    {"role": "USER", "content": "Hello"},
                ],
                "tools": [{"name": "render_json_validate_tool"}],
            }
        )

        self.assertEqual("2.0", payload["protocolVersion"])
        self.assertEqual("1.0", payload["sourceProtocolVersion"])
        self.assertEqual("legacy-chat-agent", payload["rootAgent"]["metadata"]["code"])
        self.assertEqual("Be exact.", payload["rootAgent"]["spec"]["instructions"]["text"])
        self.assertEqual("Hello", payload["run"]["input"])

    def test_application_replay_keeps_assistant_history_and_deduplicates_current_user(self) -> None:
        replay = build_application_input(
            [
                {"role": 2, "content": "previous question"},
                {"role": 3, "content": "previous answer"},
                {"role": 2, "content": "current question"},
            ],
            "current question",
        )

        self.assertIn({"role": "assistant", "content": "previous answer"}, replay)
        self.assertEqual(
            1,
            sum(1 for item in replay if item == {"role": "user", "content": "current question"}),
        )

    def test_application_replay_does_not_duplicate_system_instructions(self) -> None:
        replay = build_application_input(
            [
                {"role": "system", "content": "Already compiled into Agent instructions."},
                {"role": "user", "content": "question"},
            ],
            "question",
        )

        self.assertEqual([{"role": "user", "content": "question"}], replay)


if __name__ == "__main__":
    unittest.main()
