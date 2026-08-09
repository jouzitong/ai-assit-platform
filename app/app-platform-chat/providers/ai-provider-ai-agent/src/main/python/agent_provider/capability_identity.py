from __future__ import annotations

import re
from typing import Any


BUILTIN_TOOL_DISPLAY_NAMES = {
    "data_preview_query_tool": "查询数据预览",
    "data_format_validate_tool": "校验数据格式",
    "knowledge_base_search_tool": "检索知识库",
    "load_skill_resource": "读取技能资源",
    "render_json_validate_tool": "校验 Render JSON",
    "web_search_tool": "搜索网页",
}

BUILTIN_TOOL_CALL_REASONS = {
    "data_preview_query_tool": "需要核对授权数据的字段、结构和实际记录。",
    "data_format_validate_tool": "需要确认数据格式符合后续处理要求。",
    "knowledge_base_search_tool": "当前任务需要补充已授权知识库中的业务语义和事实依据。",
    "load_skill_resource": "需要读取已选技能的执行规范和资源内容。",
    "render_json_validate_tool": "需要确认生成内容符合页面渲染结构与组件契约。",
    "web_search_tool": "当前任务需要补充公开网页中的最新信息或事实依据。",
}


def runtime_tool_identity(graph: Any, sdk_name: str | None) -> dict[str, Any] | None:
    if not sdk_name:
        return None
    for descriptor in graph.gateway_tools.values():
        if descriptor.get("sdkName") == sdk_name:
            return descriptor
    for owner in graph.agents.values():
        for link in getattr(owner, "agent_tools", ()):
            target = graph.agents.get(link.target_key)
            if target is None:
                continue
            tool_key = link.tool_name or f"ask_{_safe_identifier(target.code)}"
            if tool_key == sdk_name:
                return {
                    "key": tool_key,
                    "code": tool_key,
                    "sdkName": tool_key,
                    "name": target.name,
                }
    return None


def tool_call_reason(tool_key: Any, display_name: Any = None) -> str | None:
    normalized_key = str(tool_key or "").strip()
    normalized_name = str(display_name or "").strip()
    if normalized_key in BUILTIN_TOOL_CALL_REASONS:
        return BUILTIN_TOOL_CALL_REASONS[normalized_key]
    if normalized_key.startswith("ask_") and normalized_name:
        return f"当前任务需要“{normalized_name}”的专业能力，因此发起协作。"
    if normalized_name:
        return f"当前步骤需要通过“{normalized_name}”补充、验证或执行任务所需信息。"
    if normalized_key:
        return "当前步骤需要调用工具补充、验证或执行任务所需信息。"
    return None


def _safe_identifier(value: str) -> str:
    result = re.sub(r"[^a-zA-Z0-9_]+", "_", value).strip("_").lower()
    return result or "agent"
