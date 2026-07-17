import json
import unittest
from pathlib import Path

from agent_provider.agents.catalog import definition_for
from agent_provider.agents.main_agent import resolve_main_agent
from agent_provider.compiler import compile_snapshot


FIXTURE = Path(__file__).resolve().parents[1] / "fixtures" / "agent-runtime-v2.json"


class AgentRoleTest(unittest.TestCase):
    def test_catalog_exposes_the_three_platform_specialists(self) -> None:
        self.assertEqual("requirement-analyst", definition_for("requirement-analyst").code)
        self.assertEqual("render-specialist", definition_for("render-specialist").code)
        self.assertEqual("sql-specialist", definition_for("sql-specialist").code)
        self.assertIsNone(definition_for("unknown-agent"))

        sql_builder = definition_for("sql-specialist")
        self.assertEqual(1, sql_builder.version)
        self.assertEqual("model://default-quality", sql_builder.model_ref)
        self.assertIn("readonly-sql", sql_builder.capabilities)

    def test_main_agent_is_resolved_from_the_published_snapshot(self) -> None:
        graph = compile_snapshot(json.loads(FIXTURE.read_text(encoding="utf-8")))

        self.assertIs(resolve_main_agent(graph), graph.root)

    def test_sql_role_contract_is_added_to_the_published_instruction(self) -> None:
        payload = json.loads(FIXTURE.read_text(encoding="utf-8"))
        payload["rootAgent"]["metadata"]["code"] = "sql-specialist"
        payload["rootAgent"]["metadata"]["name"] = "SQL 构建 Agent"
        payload["rootAgent"]["spec"]["collaboration"] = {"agentTools": [], "handoffs": []}

        graph = compile_snapshot(payload)

        self.assertIn("只读、安全、可解释的候选 SQL", graph.root.instructions)
        self.assertIn("不得声称已执行 SQL", graph.root.instructions)


if __name__ == "__main__":
    unittest.main()
