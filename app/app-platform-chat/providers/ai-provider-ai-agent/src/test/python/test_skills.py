import json
import hashlib
import os
import unittest
from pathlib import Path
from unittest.mock import patch

from agent_provider.compiler import compile_snapshot
from agent_provider.skills import SkillCatalog


FIXTURE = Path(__file__).resolve().parents[1] / "fixtures" / "agent-runtime-v2.json"


class SkillCatalogTest(unittest.TestCase):
    def test_reads_skill_resources_only_on_demand(self) -> None:
        graph = compile_snapshot(json.loads(FIXTURE.read_text(encoding="utf-8")))
        loaded = []

        result = graph.skill_catalog.read(
            "product-analysis",
            "templates/checklist.md",
            lambda record, path: loaded.append((record.name, path)),
        )

        self.assertIn("Acceptance criteria", result["content"])
        self.assertEqual([("product-analysis", "templates/checklist.md")], loaded)

    def test_rejects_path_traversal(self) -> None:
        graph = compile_snapshot(json.loads(FIXTURE.read_text(encoding="utf-8")))

        with self.assertRaisesRegex(ValueError, "relative path"):
            graph.skill_catalog.read("product-analysis", "../secret.txt")

    def test_reads_a_frozen_database_skill_through_the_run_scoped_gateway(self) -> None:
        content = "# Database skill\nUse safely."
        checksum = hashlib.sha256(content.encode("utf-8")).hexdigest()
        catalog = SkillCatalog.from_capabilities(
            {
                "skills": [{
                    "code": "database-skill",
                    "version": 3,
                    "checksum": "package-checksum",
                    "manifest": {
                        "files": [{
                            "path": "SKILL.md",
                            "checksum": checksum,
                            "mediaType": "text/markdown",
                        }],
                    },
                }],
            },
            {"runId": "run-1"},
            "sha256:snapshot",
        )
        captured = []

        class Response:
            def __enter__(self):
                return self

            def __exit__(self, *_args):
                return False

            def read(self):
                return json.dumps({
                    "skillCode": "database-skill",
                    "skillVersion": 3,
                    "path": "SKILL.md",
                    "mediaType": "text/markdown",
                    "checksum": checksum,
                    "encoding": "utf-8",
                    "content": content,
                }).encode("utf-8")

        def urlopen(http_request, timeout):
            captured.append((http_request, timeout))
            return Response()

        with patch.dict(os.environ, {
            "AI_AGENT_SKILL_GATEWAY_URL": "http://gateway/base/",
            "AI_AGENT_SKILL_GATEWAY_TOKEN": "token-value",
        }, clear=False), patch("agent_provider.skills.request.urlopen", urlopen):
            resource = catalog.read("skill://database-skill/v3")

        self.assertEqual(content, resource["content"])
        self.assertEqual(checksum, resource["checksum"])
        self.assertEqual(
            "http://gateway/base/api/v1/ai/skill-gateway/database-skill/versions/3/resources/read",
            captured[0][0].full_url,
        )
        body = json.loads(captured[0][0].data)
        self.assertEqual("run-1", body["run"]["runId"])
        self.assertEqual("sha256:snapshot", body["run"]["snapshotHash"])


if __name__ == "__main__":
    unittest.main()
