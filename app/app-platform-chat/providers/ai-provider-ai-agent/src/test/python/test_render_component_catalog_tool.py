import json
import unittest
from unittest.mock import patch

from agent_provider.tools.render_component_catalog_tool import (
    fetch_component_catalog,
    normalize_component_contract,
    parse_component_keys_json,
)


DOCUMENT = """# 折线图

## 4. 参数契约

| 参数 | 类型 | 必填 | 纳入资产 | 当前默认值 | 说明 |
| --- | --- | --- | --- | --- | --- |
| categories | Array<string \\| number> | 是 | 是 | [\"1月\"] | 横轴分类 |
| smooth | boolean | 否 | 是 | true | 是否平滑 |

## 5. 当前配置示例

## 6. 事件契约

| 事件 | 说明 |
| --- | --- |
| reload | 请求刷新数据。 |

## 7. 使用指引与限制
"""


class RenderComponentCatalogToolTest(unittest.TestCase):
    def test_parses_the_persisted_component_asset_into_a_contract(self) -> None:
        envelope = {
            "schemaVersion": "component-asset/v1",
            "sourceComponent": {"key": "line-chart-renderer", "version": "1.0.0"},
            "props": {"categories": ["1月"], "smooth": True},
            "asset": {},
        }

        component = normalize_component_contract({
            "componentKey": "line-chart-renderer",
            "name": "折线图",
            "category": "数据可视化",
            "sourceRevision": "sha256:catalog-item",
            "docMarkdown": DOCUMENT,
            "exampleJson": json.dumps(envelope, ensure_ascii=False),
        })

        self.assertTrue(component["contractAvailable"])
        self.assertEqual("line-chart-renderer", component["key"])
        self.assertEqual("1.0.0", component["componentVersion"])
        self.assertEqual(["categories", "smooth"], [item["key"] for item in component["parameters"]])
        self.assertTrue(component["parameters"][0]["required"])
        self.assertEqual(["reload"], [item["name"] for item in component["events"]])

    def test_accepts_the_java_component_key_shape_and_keeps_catalog_identity(self) -> None:
        envelope = {
            "schemaVersion": "component-asset/v1",
            "sourceComponent": {"key": "source-alias", "version": "1.0.0"},
            "props": {},
            "asset": {},
        }
        component = normalize_component_contract({
            "componentKey": "published-renderer",
            "docMarkdown": DOCUMENT,
            "exampleJson": json.dumps(envelope),
        })

        self.assertEqual("published-renderer", component["key"])
        self.assertEqual("published-renderer", component["example"]["component"])

    def test_fails_closed_when_the_java_catalog_revision_is_missing(self) -> None:
        with patch(
            "agent_provider.tools.render_component_catalog_tool.post_platform_json",
            return_value={"success": True, "data": {"components": []}},
        ):
            result = fetch_component_catalog({})

        self.assertFalse(result["success"])
        self.assertEqual("COMPONENT_CATALOG_REVISION_MISSING", result["errorCode"])

    def test_returns_only_a_versioned_machine_readable_live_contract(self) -> None:
        envelope = {
            "schemaVersion": "component-asset/v1",
            "sourceComponent": {"key": "line-chart-renderer", "version": "1.0.0"},
            "props": {"categories": []},
            "asset": {},
        }
        response = {
            "success": True,
            "data": {
                "catalogRevision": "sha256:" + "c" * 64,
                "components": [{
                    "componentKey": "line-chart-renderer",
                    "sourceRevision": "sha256:" + "d" * 64,
                    "docMarkdown": DOCUMENT,
                    "exampleJson": json.dumps(envelope),
                }],
            },
        }
        with patch(
            "agent_provider.tools.render_component_catalog_tool.post_platform_json",
            return_value=response,
        ):
            result = fetch_component_catalog({}, component_keys=["line-chart-renderer"])

        self.assertTrue(result["success"])
        self.assertEqual("1.0.0", result["components"][0]["componentVersion"])
        self.assertTrue(result["components"][0]["contractAvailable"])

    def test_rejects_a_published_component_without_a_version(self) -> None:
        response = {
            "success": True,
            "data": {
                "catalogRevision": "sha256:" + "c" * 64,
                "components": [{
                    "componentKey": "line-chart-renderer",
                    "sourceRevision": "sha256:" + "d" * 64,
                    "docMarkdown": DOCUMENT,
                    "exampleJson": "{}",
                }],
            },
        }
        with patch(
            "agent_provider.tools.render_component_catalog_tool.post_platform_json",
            return_value=response,
        ):
            result = fetch_component_catalog({})

        self.assertFalse(result["success"])
        self.assertEqual("COMPONENT_VERSION_MISSING", result["errorCode"])

    def test_rejects_non_array_component_key_json(self) -> None:
        keys, error = parse_component_keys_json('{"key":"line"}')

        self.assertEqual([], keys)
        self.assertIn("JSON array", error)


if __name__ == "__main__":
    unittest.main()
