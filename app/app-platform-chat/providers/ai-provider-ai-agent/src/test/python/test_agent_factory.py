import json
import unittest
from pathlib import Path

from agent_provider.agents.factory import AgentFactory
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


if __name__ == "__main__":
    unittest.main()
