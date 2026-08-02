import importlib
import json
import sys
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from agent_provider.agents.dispatcher import AgentDispatcher
from agent_provider.agents.factory import AgentFactory
from agent_provider.artifacts import RunArtifactCollector, render_document_hash
from agent_provider.compiler import compile_snapshot
from agent_provider.events import EventEmitter


FIXTURE = Path(__file__).resolve().parents[1] / "fixtures" / "agent-runtime-v2.json"


class AgentFactoryTest(unittest.TestCase):
    def test_specialist_agent_tool_is_built_only_after_delegation(self) -> None:
        payload = json.loads(FIXTURE.read_text(encoding="utf-8"))
        payload["rootAgent"]["spec"]["collaboration"]["handoffs"] = []
        graph = compile_snapshot(payload)
        target_key = graph.root.agent_tools[0].target_key

        sdk_graph = AgentFactory(graph, EventEmitter(graph.payload)).build_root()

        self.assertIn(graph.root_key, sdk_graph.agents)
        self.assertNotIn(target_key, sdk_graph.agents)
        self.assertTrue(any(tool.name == "ask_requirement_reviewer" for tool in sdk_graph.root.tools))

        specialist = sdk_graph.agent_for_key(target_key)

        self.assertIsNotNone(specialist)
        self.assertIn(target_key, sdk_graph.agents)

    def test_knowledge_search_tool_is_bound_to_the_current_run_catalog(self) -> None:
        payload = json.loads(FIXTURE.read_text(encoding="utf-8"))
        payload["rootAgent"]["spec"]["toolRefs"].append({"ref": "knowledge_base_search_tool"})
        payload["run"]["context"] = {
            "knowledgeBases": [
                {
                    "kbCode": "product-manual",
                    "name": "Product manual",
                    "description": "Product usage and setup.",
                    "tags": ["product"],
                }
            ]
        }
        graph = compile_snapshot(payload)

        sdk_graph = AgentFactory(graph, EventEmitter(graph.payload)).build_root()

        self.assertTrue(any(tool.name == "knowledge_base_search_tool" for tool in sdk_graph.root.tools))
        self.assertIn('"kbCode":"product-manual"', graph.root.instructions)
        self.assertIn("only with an exact kb_code", graph.root.instructions)

    def test_knowledge_search_tool_is_not_exposed_without_an_available_knowledge_base(self) -> None:
        payload = json.loads(FIXTURE.read_text(encoding="utf-8"))
        payload["rootAgent"]["spec"]["toolRefs"].append({"ref": "knowledge_base_search_tool"})
        graph = compile_snapshot(payload)

        sdk_graph = AgentFactory(graph, EventEmitter(graph.payload)).build_root()

        self.assertFalse(any(tool.name == "knowledge_base_search_tool" for tool in sdk_graph.root.tools))
        self.assertIn("No knowledge base is available", graph.root.instructions)


class AgentDispatcherEvidenceTest(unittest.IsolatedAsyncioTestCase):
    async def test_specialist_artifacts_are_collected_without_changing_tool_result(self) -> None:
        target = SimpleNamespace(
            code="dashboard-builder",
            version=1,
            name="Dashboard builder",
            description="Builds render artifacts.",
        )
        link = SimpleNamespace(
            target_key="dashboard-builder-key",
            tool_name="ask_dashboard_builder",
            description=None,
        )
        owner = SimpleNamespace(agent_tools=[link])
        graph = SimpleNamespace(
            agents={"dashboard-builder-key": target},
            gateway_tools={},
            max_turns=3,
            payload={"confidencePolicy": {"enabled": False}, "run": {"context": {}}},
        )
        envelope = json.dumps(
            {
                "artifacts": [
                    {
                        "artifactCode": "render-document",
                        "artifactType": "RENDER_JSON",
                        "content": {"component": "Table"},
                    },
                    {
                        "artifactCode": "data-preview",
                        "content": {"forged": True},
                    },
                    {
                        "artifactCode": "validation-report",
                        "content": {"forged": True},
                    },
                ]
            }
        )
        preview = {
            "tool": "data_preview_query_tool",
            "success": True,
            "model": "ods_trade_account_user_address",
            "records": [{"address": "trusted"}],
        }
        validated_document = {"component": "Table"}
        validation = {
            "tool": "render_json_validate_tool",
            "valid": True,
            "documentHash": render_document_hash(validated_document),
            "renderDocument": validated_document,
        }

        class ChildResult:
            final_output = envelope

            async def stream_events(self):
                for call_id, tool_code, output in (
                    ("preview-1", "data_preview_query_tool", preview),
                    ("validate-1", "render_json_validate_tool", validation),
                ):
                    yield SimpleNamespace(
                        type="run_item_stream_event",
                        name="tool_called",
                        item=SimpleNamespace(
                            raw_item=SimpleNamespace(name=tool_code, call_id=call_id)
                        ),
                    )
                    yield SimpleNamespace(
                        type="run_item_stream_event",
                        name="tool_output",
                        item=SimpleNamespace(
                            raw_item=SimpleNamespace(
                                type="function_call_output",
                                call_id=call_id,
                            ),
                            output=output,
                        ),
                    )

        class FakeRunner:
            @staticmethod
            def run_streamed(*args: object, **kwargs: object) -> ChildResult:
                return ChildResult()

        def function_tool(**kwargs: object):
            return lambda function: function

        frames: list[dict[str, object]] = []
        collector = RunArtifactCollector()
        confidence_guard_module = importlib.import_module(
            "agent_provider.runtime.confidence_guard"
        )
        dispatcher = AgentDispatcher(
            graph,
            EventEmitter(graph.payload, frames.append),
            build_agent=lambda key: object(),
            compiled_for=lambda agent: target,
            function_tool=function_tool,
            artifact_collector=collector,
        )

        with (
            patch.dict(sys.modules, {"agents": SimpleNamespace(Runner=FakeRunner)}),
            patch.object(
                confidence_guard_module,
                "guard_output",
                new=AsyncMock(return_value=SimpleNamespace(text="Dashboard artifact is ready.")),
            ),
        ):
            result = await dispatcher.tools_for(owner)[0]("Build the account list")

        self.assertEqual("Dashboard artifact is ready.", result)
        self.assertEqual("render-document", collector.snapshot()[0]["artifactCode"])
        self.assertEqual("Table", collector.snapshot()[0]["content"]["component"])
        self.assertEqual(
            ["data-preview", "render-document", "validation-report"],
            [proof["artifactCode"] for proof in collector.proof_snapshot()],
        )
        self.assertEqual("trusted", collector.proof_snapshot()[0]["content"]["records"][0]["address"])
        self.assertEqual(
            [
                "agent.delegated",
                "tool.started",
                "tool.completed",
                "tool.started",
                "tool.completed",
                "agent.delegation.completed",
            ],
            [frame["eventType"] for frame in frames],
        )

    async def test_specialist_knowledge_result_is_reused_by_its_confidence_guard(self) -> None:
        target = SimpleNamespace(
            code="schema-specialist",
            version=1,
            name="Schema specialist",
            description="Answers schema questions.",
        )
        link = SimpleNamespace(
            target_key="schema-specialist-key",
            tool_name="ask_schema_specialist",
            description=None,
        )
        owner = SimpleNamespace(agent_tools=[link])
        graph = SimpleNamespace(
            agents={"schema-specialist-key": target},
            gateway_tools={},
            max_turns=3,
            payload={
                "confidencePolicy": {
                    "enabled": True,
                    "scoring": {"enabled": True},
                },
                "run": {
                    "context": {
                        "knowledgeBases": [{"kbCode": "db-schema", "name": "Database schema"}],
                    }
                },
            },
        )
        kb_result = {
            "success": True,
            "kbCode": "db-schema",
            "query": "user table fields",
            "items": [{"documentId": "doc-1", "content": "user table: id bigint"}],
        }

        class ChildResult:
            final_output = "The user table has an id field."

            async def stream_events(self):
                yield SimpleNamespace(
                    type="run_item_stream_event",
                    name="tool_called",
                    item=SimpleNamespace(
                        raw_item=SimpleNamespace(
                            name="knowledge_base_search_tool",
                            call_id="call-child-kb",
                        )
                    ),
                )
                yield SimpleNamespace(
                    type="run_item_stream_event",
                    name="tool_output",
                    item=SimpleNamespace(
                        raw_item=SimpleNamespace(
                            type="function_call_output",
                            call_id="call-child-kb",
                        ),
                        output=kb_result,
                    ),
                )

        class FakeRunner:
            @staticmethod
            def run_streamed(*args: object, **kwargs: object) -> ChildResult:
                return ChildResult()

        def function_tool(**kwargs: object):
            return lambda function: function

        guard = AsyncMock(return_value=SimpleNamespace(text="Guarded specialist answer."))
        confidence_guard_module = importlib.import_module(
            "agent_provider.runtime.confidence_guard"
        )
        dispatcher = AgentDispatcher(
            graph,
            EventEmitter(graph.payload, lambda frame: None),
            build_agent=lambda key: object(),
            compiled_for=lambda agent: target,
            function_tool=function_tool,
        )

        with (
            patch.dict(sys.modules, {"agents": SimpleNamespace(Runner=FakeRunner)}),
            patch.object(confidence_guard_module, "guard_output", new=guard),
        ):
            result = await dispatcher.tools_for(owner)[0]("List the user table fields")

        self.assertEqual("Guarded specialist answer.", result)
        guard.assert_awaited_once()
        initial_evidence = guard.await_args.kwargs["initial_evidence"]
        self.assertEqual("db-schema", initial_evidence["kbCode"])
        self.assertEqual(["db-schema"], initial_evidence["kbCodes"])
        self.assertEqual("doc-1", initial_evidence["items"][0]["documentId"])


if __name__ == "__main__":
    unittest.main()
