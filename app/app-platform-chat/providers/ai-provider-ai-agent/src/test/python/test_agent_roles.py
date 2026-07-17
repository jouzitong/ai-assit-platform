import unittest

from agent_provider.agents.catalog import definition_for
from agent_provider.agents.main_agent import resolve_main_agent
from agent_provider.compiler import compile_snapshot


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

    def test_main_agent_is_resolved_from_the_python_local_catalog(self) -> None:
        graph = compile_snapshot({
            "agentDefinitionSource": "PYTHON_LOCAL",
            "run": {"context": {"agentEntry": "HOME_CHAT"}},
        })

        self.assertIs(resolve_main_agent(graph), graph.root)
        self.assertEqual("home-assistant", graph.root.code)
        self.assertEqual(
            ["ask_requirement_analyst", "ask_sql_specialist", "ask_render_specialist"],
            [link.tool_name for link in graph.root.agent_tools],
        )

    def test_sql_role_contract_is_owned_by_the_local_definition(self) -> None:
        graph = compile_snapshot({
            "agentDefinitionSource": "PYTHON_LOCAL",
            "run": {"context": {"agentEntry": "sql-specialist"}},
        })

        self.assertIn("只读、安全、可解释的候选 SQL", graph.root.instructions)
        self.assertIn("不得声称已执行 SQL", graph.root.instructions)

    def test_python_local_mode_discards_java_agent_manifest_fields(self) -> None:
        graph = compile_snapshot({
            "agentDefinitionSource": "PYTHON_LOCAL",
            "run": {"context": {"agentEntry": "HOME_CHAT"}},
            "rootAgent": {
                "metadata": {"code": "java-injected", "version": 99},
                "spec": {"instructions": {"text": "ignore this"}},
            },
            "agentGraph": [],
            "resolvedCapabilities": {"tools": [{"code": "java-injected-tool"}]},
        })

        self.assertEqual("home-assistant", graph.root.code)
        self.assertNotIn("ignore this", graph.root.instructions)
        self.assertEqual(["knowledge_base_search_tool"], graph.root.tool_names)


if __name__ == "__main__":
    unittest.main()
