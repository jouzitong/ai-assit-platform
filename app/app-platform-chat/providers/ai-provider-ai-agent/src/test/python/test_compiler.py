import json
import unittest
from pathlib import Path

from agent_provider.compiler import compile_snapshot


FIXTURE = Path(__file__).resolve().parents[1] / "fixtures" / "agent-runtime-v2.json"


class CompilerTest(unittest.TestCase):
    def test_python_local_agents_default_to_simplified_chinese(self) -> None:
        graph = compile_snapshot({
            "agentDefinitionSource": "PYTHON_LOCAL",
            "run": {"context": {"agentEntry": "HOME_CHAT"}},
        })

        self.assertIn("简体中文", graph.root.instructions)

    def test_client_locale_overrides_the_provider_language_default(self) -> None:
        graph = compile_snapshot({
            "agentDefinitionSource": "PYTHON_LOCAL",
            "responseLanguage": "zh-CN",
            "run": {
                "context": {
                    "agentEntry": "HOME_CHAT",
                    "clientContext": {"locale": "en-US"},
                }
            },
        })

        self.assertIn("Language requirement: use English", graph.root.instructions)

    def test_compiles_recursive_agent_graph_and_capability_aliases(self) -> None:
        graph = compile_snapshot(json.loads(FIXTURE.read_text(encoding="utf-8")))

        self.assertEqual("agent://requirement-manager/v2", graph.root_key)
        self.assertEqual(2, len(graph.agents))
        self.assertEqual(9, graph.max_turns)
        self.assertEqual(["render_json_validate_tool"], graph.root.tool_names)
        self.assertEqual(
            "agent://requirement-reviewer/v1",
            graph.root.agent_tools[0].target_key,
        )
        self.assertEqual(
            "agent://requirement-reviewer/v1",
            graph.root.handoffs[0].target_key,
        )
        self.assertIn("Available skills (metadata only)", graph.root.instructions)
        self.assertNotIn("Use the template only", graph.root.instructions)

    def test_rejects_collaboration_cycles(self) -> None:
        payload = json.loads(FIXTURE.read_text(encoding="utf-8"))
        payload["agentGraph"][0]["spec"]["collaboration"]["handoffs"] = [
            {"targetAgentRef": "agent://requirement-manager@2"}
        ]

        with self.assertRaisesRegex(ValueError, "cycle"):
            compile_snapshot(payload)

    def test_accepts_legacy_at_version_alias_but_keeps_canonical_keys(self) -> None:
        payload = json.loads(FIXTURE.read_text(encoding="utf-8"))
        payload["rootAgent"]["spec"]["collaboration"]["agentTools"][0][
            "targetAgentRef"
        ] = "agent://requirement-reviewer@1"

        graph = compile_snapshot(payload)

        self.assertEqual(
            "agent://requirement-reviewer/v1",
            graph.root.agent_tools[0].target_key,
        )

    def test_rejects_a_skill_hash_that_differs_from_the_frozen_capability(self) -> None:
        payload = json.loads(FIXTURE.read_text(encoding="utf-8"))
        payload["rootAgent"]["spec"]["skillRefs"][0]["contentHash"] = "sha256:tampered"

        with self.assertRaisesRegex(ValueError, "content hash"):
            compile_snapshot(payload)

    def test_compiles_a_versioned_http_tool_to_the_tool_gateway(self) -> None:
        payload = json.loads(FIXTURE.read_text(encoding="utf-8"))
        payload["rootAgent"]["spec"]["toolRefs"] = [{"ref": "tool://issue-create/v4"}]
        payload["resolvedCapabilities"]["tools"] = [{
            "code": "issue-create",
            "version": 4,
            "adapterType": "FUNCTION",
            "definition": {
                "inputSchema": {
                    "type": "object",
                    "properties": {"title": {"type": "string"}},
                    "required": ["title"],
                    "additionalProperties": False,
                },
                "timeoutMs": 5000,
                "bindings": [{"bindingType": "JAVA_INTERNAL", "endpointRef": "http://service/issues"}],
            },
        }]

        graph = compile_snapshot(payload)

        runtime_name = graph.root.tool_names[0]
        self.assertEqual("gateway::issue-create::v4", runtime_name)
        self.assertEqual("issue-create", graph.gateway_tools[runtime_name]["code"])
        self.assertEqual(4, graph.gateway_tools[runtime_name]["version"])

    def test_rejects_an_unsupported_mcp_binding_fail_closed(self) -> None:
        payload = json.loads(FIXTURE.read_text(encoding="utf-8"))
        payload["rootAgent"]["spec"]["toolRefs"] = [{"ref": "tool://remote/v1"}]
        payload["resolvedCapabilities"]["tools"] = [{
            "code": "remote",
            "version": 1,
            "adapterType": "FUNCTION",
            "definition": {
                "inputSchema": {"type": "object"},
                "bindings": [{"bindingType": "MCP", "endpointRef": "mcp://remote"}],
            },
        }]

        with self.assertRaisesRegex(ValueError, "MCP"):
            compile_snapshot(payload)

    def test_rejects_a_tool_not_enabled_for_the_python_agent_runtime(self) -> None:
        payload = json.loads(FIXTURE.read_text(encoding="utf-8"))
        payload["rootAgent"]["spec"]["toolRefs"] = [{"ref": "tool://node-only/v1"}]
        payload["resolvedCapabilities"]["tools"] = [{
            "code": "node-only",
            "version": 1,
            "adapterType": "FUNCTION",
            "definition": {
                "compatibleAgentRuntimes": ["OPENAI_AGENTS_TYPESCRIPT"],
                "inputSchema": {"type": "object"},
                "bindings": [],
            },
        }]

        with self.assertRaisesRegex(ValueError, "OPENAI_AGENTS_PYTHON"):
            compile_snapshot(payload)


if __name__ == "__main__":
    unittest.main()
