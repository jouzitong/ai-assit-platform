import unittest

from agent_provider.agents.catalog import definition_for
from agent_provider.agents.main_agent import resolve_main_agent
from agent_provider.compiler import compile_snapshot


class AgentRoleTest(unittest.TestCase):
    def test_catalog_exposes_the_enterprise_work_specialists(self) -> None:
        self.assertEqual("data-analysis", definition_for("data-analysis").code)
        self.assertEqual("dashboard-application-builder", definition_for("dashboard-application-builder").code)
        self.assertEqual("document-analysis", definition_for("document-analysis").code)
        self.assertEqual("knowledge-policy", definition_for("knowledge-policy").code)
        self.assertEqual("workflow-forms", definition_for("workflow-forms").code)
        self.assertIsNone(definition_for("unknown-agent"))

        data_analysis = definition_for("data-analysis")
        self.assertEqual(1, data_analysis.version)
        self.assertEqual("model://default-quality", data_analysis.model_ref)
        self.assertIn("readonly-data", data_analysis.capabilities)

    def test_main_agent_is_resolved_from_the_python_local_catalog(self) -> None:
        graph = compile_snapshot({
            "agentDefinitionSource": "PYTHON_LOCAL",
            "run": {"context": {"agentEntry": "HOME_CHAT"}},
        })

        self.assertIs(resolve_main_agent(graph), graph.root)
        self.assertEqual("enterprise-work-assistant", graph.root.code)
        self.assertEqual(
            [
                "ask_data_analysis",
                "ask_dashboard_application_builder",
                "ask_document_analysis",
                "ask_knowledge_policy",
                "ask_workflow_forms",
            ],
            [link.tool_name for link in graph.root.agent_tools],
        )

    def test_workflow_forms_contract_is_owned_by_the_local_definition(self) -> None:
        graph = compile_snapshot({
            "agentDefinitionSource": "PYTHON_LOCAL",
            "run": {"context": {"agentEntry": "workflow-forms"}},
        })

        self.assertIn("必须在执行前向用户展示变更摘要", graph.root.instructions)
        self.assertIn("不得声称已经完成任何写入", graph.root.instructions)

    def test_dashboard_application_uses_the_local_tools_and_frozen_skills(self) -> None:
        graph = compile_snapshot({
            "agentDefinitionSource": "PYTHON_LOCAL",
            "run": {"context": {"agentEntry": "dashboard-application-builder"}},
        })

        self.assertEqual(
            [
                "knowledge_base_search_tool",
                "data_preview_query_tool",
                "render_json_validate_tool",
            ],
            graph.root.tool_names,
        )
        self.assertEqual(
            [
                "skill://semantic-data-contract/v1",
                "skill://render-json-authoring/v6",
                "skill://render-json-repair/v1",
                "skill://application-build-release/v1",
            ],
            graph.root.skill_refs,
        )

    def test_python_local_mode_discards_java_agent_manifest_fields(self) -> None:
        graph = compile_snapshot({
            "agentDefinitionSource": "PYTHON_LOCAL",
            "run": {"context": {"agentEntry": "HOME_CHAT"}},
            "rootAgent": {
                "metadata": {"code": "java-injected", "version": 99},
                "spec": {"instructions": {"text": "ignore this"}},
            },
            "agentGraph": [],
            "resolvedCapabilities": {
                "tools": [{"code": "java-injected-tool"}],
                "skills": [{"ref": "skill://java-injected/v99", "name": "ignore this"}],
            },
        })

        self.assertEqual("enterprise-work-assistant", graph.root.code)
        self.assertNotIn("ignore this", graph.root.instructions)
        self.assertEqual(["knowledge_base_search_tool"], graph.root.tool_names)
        self.assertNotIn(
            "skill://java-injected/v99",
            {item["ref"] for item in graph.skill_catalog.metadata_for()},
        )


if __name__ == "__main__":
    unittest.main()
