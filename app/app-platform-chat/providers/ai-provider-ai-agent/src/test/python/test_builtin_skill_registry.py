import json
import tempfile
import unittest
from pathlib import Path

from agent_provider.skills.registry import built_in_skill_capabilities
from agent_provider.skills.registry import built_in_skill_roots


BUILT_IN_SKILL_ROOT = (
    Path(__file__).resolve().parents[2] / "main" / "python" / "agent_provider" / "skills"
)
APPLICATION_SKILLS = {
    "semantic-data-contract",
    "render-json-generation",
    "application-build-release",
}
RENDER_JSON_GENERATION_CASES = {
    "combo-chart": "combo-chart-renderer.md",
    "form-edit": "form-main-layout.md",
    "line-chart": "line-chart-renderer.md",
    "list-table": "zg-list-main-layout.md",
    "radar-chart": "radar-chart-renderer.md",
}
EXPECTED_SCHEMA_TITLES = {
    "ApplicationBrief",
    "DataContract",
    "ApplicationBuildState",
}


class BuiltInSkillRegistryTest(unittest.TestCase):
    def test_freezes_all_package_local_skills_in_stable_order(self) -> None:
        capabilities = built_in_skill_capabilities(BUILT_IN_SKILL_ROOT)

        codes = [item["code"] for item in capabilities]
        self.assertEqual(sorted(codes), codes)
        self.assertTrue(APPLICATION_SKILLS.issubset(codes))
        for capability in capabilities:
            self.assertEqual(
                f"skill://{capability['code']}/v{capability['version']}",
                capability["ref"],
            )
            self.assertRegex(capability["contentHash"], r"^sha256:[a-f0-9]{64}$")
            root = Path(capability["rootPath"])
            self.assertEqual(root.name, capability["code"])
            self.assertTrue(root.is_relative_to(BUILT_IN_SKILL_ROOT.resolve()))
            files = capability["manifest"]["files"]
            self.assertEqual(sorted(item["path"] for item in files), [item["path"] for item in files])
            self.assertIn("SKILL.md", {item["path"] for item in files})
            for item in files:
                resource = root / item["path"]
                self.assertEqual(resource.stat().st_size, item["size"])
                self.assertRegex(item["checksum"], r"^sha256:[a-f0-9]{64}$")

    def test_content_hash_is_independent_of_manifest_file_order(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            skill_root = self._write_skill(root)
            first = built_in_skill_capabilities(root)[0]

            manifest_path = skill_root / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["files"].reverse()
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            second = built_in_skill_capabilities(root)[0]

            self.assertEqual(first["contentHash"], second["contentHash"])
            self.assertEqual(first["manifest"], second["manifest"])

    def test_content_hash_changes_when_a_declared_resource_changes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            skill_root = self._write_skill(root)
            first_hash = built_in_skill_capabilities(root)[0]["contentHash"]

            (skill_root / "assets" / "schema.json").write_text(
                '{"title":"Changed"}',
                encoding="utf-8",
            )
            second_hash = built_in_skill_capabilities(root)[0]["contentHash"]

            self.assertNotEqual(first_hash, second_hash)

    def test_rejects_an_escaping_manifest_resource(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            skill_root = self._write_skill(root)
            manifest_path = skill_root / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["files"].append({
                "path": "../outside.txt",
                "mediaType": "text/plain",
            })
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "normalized relative path"):
                built_in_skill_capabilities(root)

    def test_rejects_a_skill_without_a_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            skill_root = root / "missing-manifest"
            skill_root.mkdir()
            (skill_root / "SKILL.md").write_text("# Missing", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "manifest not found"):
                built_in_skill_capabilities(root)

    def test_root_discovery_ignores_non_skill_directories(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "ordinary-directory").mkdir()
            skill_root = self._write_skill(root)

            self.assertEqual([skill_root], built_in_skill_roots(root))

    @staticmethod
    def _write_skill(root: Path) -> Path:
        skill_root = root / "sample-skill"
        (skill_root / "assets").mkdir(parents=True)
        (skill_root / "SKILL.md").write_text("# Sample skill\n", encoding="utf-8")
        (skill_root / "assets" / "schema.json").write_text(
            '{"title":"Sample"}',
            encoding="utf-8",
        )
        manifest = {
            "code": "sample-skill",
            "version": 1,
            "name": "Sample Skill",
            "description": "A test skill.",
            "files": [
                {"path": "SKILL.md", "mediaType": "text/markdown"},
                {"path": "assets/schema.json", "mediaType": "application/json"},
            ],
        }
        (skill_root / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        return skill_root


class ApplicationBuildSkillPackageTest(unittest.TestCase):
    def test_application_skill_manifests_declare_every_resource(self) -> None:
        for code in APPLICATION_SKILLS:
            skill_root = BUILT_IN_SKILL_ROOT / code
            manifest = json.loads((skill_root / "manifest.json").read_text(encoding="utf-8"))
            declared = {item["path"] for item in manifest["files"]}
            actual = {
                str(path.relative_to(skill_root))
                for path in skill_root.rglob("*")
                if path.is_file() and path.name != "manifest.json"
            }

            self.assertEqual(actual, declared, code)

    def test_generation_skill_has_a_frozen_fixture_and_reference_for_each_case(self) -> None:
        skill_root = BUILT_IN_SKILL_ROOT / "render-json-generation"

        for case_id, reference in RENDER_JSON_GENERATION_CASES.items():
            with self.subTest(case_id=case_id):
                fixture = json.loads(
                    (skill_root / "assets" / "component-test-cases" / f"{case_id}.json").read_text(
                        encoding="utf-8"
                    )
                )
                self.assertEqual(case_id, fixture["caseId"])
                self.assertIsInstance(fixture["document"], dict)
                self.assertIsInstance(fixture["document"]["root"], dict)
                self.assertTrue((skill_root / "references" / "components" / reference).is_file())

    def test_remaining_stage_schema_assets_are_valid_json(self) -> None:
        schemas = []
        for code in APPLICATION_SKILLS:
            for path in (BUILT_IN_SKILL_ROOT / code / "assets").glob("*.schema.json"):
                schema = json.loads(path.read_text(encoding="utf-8"))
                self.assertEqual(
                    "https://json-schema.org/draft/2020-12/schema",
                    schema["$schema"],
                )
                self.assertEqual("object", schema["type"])
                self.assertFalse(schema["additionalProperties"])
                schemas.append(schema)

        self.assertEqual(EXPECTED_SCHEMA_TITLES, {schema["title"] for schema in schemas})

    def test_skill_frontmatter_contains_only_name_and_description(self) -> None:
        for code in APPLICATION_SKILLS:
            content = (BUILT_IN_SKILL_ROOT / code / "SKILL.md").read_text(encoding="utf-8")
            _, frontmatter, _ = content.split("---", 2)
            keys = {
                line.split(":", 1)[0].strip()
                for line in frontmatter.splitlines()
                if line.strip()
            }

            self.assertEqual({"name", "description"}, keys, code)


if __name__ == "__main__":
    unittest.main()
