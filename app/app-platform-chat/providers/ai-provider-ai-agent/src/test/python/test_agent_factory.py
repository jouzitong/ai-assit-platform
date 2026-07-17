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


if __name__ == "__main__":
    unittest.main()
