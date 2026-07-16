import unittest
from pathlib import Path
from types import SimpleNamespace

from agent_provider.events import EventEmitter, map_run_item_event
from agent_provider.runtime import extract_artifacts, extract_usage


ARTIFACT_FIXTURE = Path(__file__).resolve().parents[1] / "fixtures" / "agent-runtime-artifact-result.json"


class EventsAndUsageTest(unittest.TestCase):
    def test_emits_platform_event_envelope(self) -> None:
        frames = []
        emitter = EventEmitter(
            {
                "run": {
                    "runId": "run-1",
                    "requestId": "request-1",
                    "traceId": "trace-1",
                }
            },
            frames.append,
        )

        emitter.event("assistant.message.delta", status="RUNNING", delta="hello")

        self.assertEqual("event", frames[0]["type"])
        self.assertEqual("2.0", frames[0]["protocolVersion"])
        self.assertEqual("assistant.message.delta", frames[0]["eventType"])
        self.assertEqual("run-1", frames[0]["runId"])

    def test_maps_sdk_tool_failure_without_exposing_raw_item(self) -> None:
        raw = SimpleNamespace(name="validator", call_id="call-1", arguments='{"x":1}')
        item = SimpleNamespace(raw_item=raw, output={"success": False, "error": "bad"})
        mapped = map_run_item_event(SimpleNamespace(name="tool_output", item=item))

        self.assertEqual("tool.failed", mapped[0])
        self.assertEqual("FAILED", mapped[1])
        self.assertEqual("call-1", mapped[3]["callId"])

    def test_maps_gateway_sdk_names_back_to_versioned_platform_tool_identity(self) -> None:
        raw = SimpleNamespace(name="gateway_issue_create_v4", call_id="call-2")
        item = SimpleNamespace(raw_item=raw, output={"status": "SUCCESS"})
        mapped = map_run_item_event(
            SimpleNamespace(name="tool_output", item=item),
            lambda name: {
                "code": "issue-create",
                "version": 4,
            } if name == "gateway_issue_create_v4" else None,
        )

        self.assertEqual("issue-create", mapped[3]["toolCode"])
        self.assertEqual(4, mapped[3]["toolVersion"])

    def test_extracts_actual_usage_from_sdk_context(self) -> None:
        usage = SimpleNamespace(input_tokens=11, output_tokens=7, total_tokens=18)
        result = SimpleNamespace(context_wrapper=SimpleNamespace(usage=usage))

        self.assertEqual(
            {"inputTokens": 11, "outputTokens": 7, "totalTokens": 18},
            extract_usage(result),
        )

    def test_extracts_normalized_artifacts_from_json_string_output(self) -> None:
        final_output = ARTIFACT_FIXTURE.read_text(encoding="utf-8")

        artifacts = extract_artifacts(final_output)

        self.assertEqual(1, len(artifacts))
        self.assertEqual("render-document", artifacts[0]["artifactCode"])
        self.assertEqual("RENDER_JSON", artifacts[0]["artifactType"])
        self.assertEqual("JSON", artifacts[0]["contentFormat"])
        self.assertEqual("Text", artifacts[0]["content"]["component"])

    def test_extracts_artifacts_from_one_complete_markdown_json_fence(self) -> None:
        final_output = """```json
        {"artifacts":[{"code":"checked","content":{"ok":true}}]}
        ```"""

        artifacts = extract_artifacts(final_output)

        self.assertEqual("checked", artifacts[0]["artifactCode"])
        self.assertEqual("JSON", artifacts[0]["contentFormat"])

    def test_extracts_artifacts_from_a_model_dump_without_pydantic_options(self) -> None:
        class OutputModel:
            def model_dump(self):
                return {"artifacts": [{"code": "checked", "content": {"ok": True}}]}

        artifacts = extract_artifacts(OutputModel())

        self.assertEqual("checked", artifacts[0]["artifactCode"])


if __name__ == "__main__":
    unittest.main()
