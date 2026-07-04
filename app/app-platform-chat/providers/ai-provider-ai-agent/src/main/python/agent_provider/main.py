import asyncio
import json
import os
import sys
import uuid
from typing import Any

from agents import Agent, Runner

from tools import (
    knowledge_base_search_tool,
    render_json_validate_tool,
)


TOOL_REGISTRY = {
    "knowledge_base_search_tool": knowledge_base_search_tool,
    "render_json_validate_tool": render_json_validate_tool,
}


def _build_transcript(messages: list[dict[str, Any]]) -> tuple[str, str]:
    system_parts: list[str] = []
    conversation_parts: list[str] = []
    for item in messages or []:
        if not isinstance(item, dict):
            continue
        role = str(item.get("role") or "USER").upper()
        content = item.get("content")
        if content is None:
            continue
        text = str(content).strip()
        if not text:
            continue
        if role == "SYSTEM":
            system_parts.append(text)
        else:
            conversation_parts.append(f"{role}: {text}")
    return "\n\n".join(system_parts).strip(), "\n".join(conversation_parts).strip()


def _append_json_instruction(instructions: str, response_format: dict[str, Any] | None) -> str:
    if not isinstance(response_format, dict):
        return instructions
    format_type = response_format.get("type")
    schema = response_format.get("schema")
    if str(format_type or "").upper() != "JSON_SCHEMA" or not schema:
        return instructions
    extra = (
        "Return strictly valid JSON that matches this schema. "
        "Do not wrap it in markdown.\n"
        f"{json.dumps(schema, ensure_ascii=False)}"
    )
    if instructions:
        return instructions + "\n\n" + extra
    return extra


def _resolve_enabled_tool_names(payload: dict[str, Any]) -> list[str]:
    resolved: list[str] = []
    seen: set[str] = set()

    for item in payload.get("tools") or []:
        if not isinstance(item, dict):
            continue
        name = str(item.get("name") or "").strip()
        if name and name in TOOL_REGISTRY and name not in seen:
            resolved.append(name)
            seen.add(name)

    ext = payload.get("ext")
    if isinstance(ext, dict):
        for key in ("enabledTools", "toolNames", "aiAgentTools"):
            values = ext.get(key)
            if not isinstance(values, list):
                continue
            for value in values:
                name = str(value or "").strip()
                if name and name in TOOL_REGISTRY and name not in seen:
                    resolved.append(name)
                    seen.add(name)
    return resolved


def _resolve_enabled_tools(payload: dict[str, Any]) -> tuple[list[str], list[Any]]:
    tool_names = _resolve_enabled_tool_names(payload)
    return tool_names, [TOOL_REGISTRY[name] for name in tool_names]


def _normalize_json_output(final_output: Any) -> tuple[dict[str, Any] | list[Any] | None, str | None]:
    if isinstance(final_output, (dict, list)):
        return final_output, json.dumps(final_output, ensure_ascii=False)
    if isinstance(final_output, str):
        text = final_output.strip()
        if not text:
            return None, ""
        try:
            normalized = json.loads(text)
        except json.JSONDecodeError:
            return None, text
        if isinstance(normalized, (dict, list)):
            return normalized, json.dumps(normalized, ensure_ascii=False)
        return None, text
    return None, json.dumps(final_output, ensure_ascii=False)


def _build_outputs(final_output: Any, response_format: dict[str, Any] | None) -> list[dict[str, Any]]:
    format_type = str((response_format or {}).get("type") or "").upper()
    normalized_json, text_output = _normalize_json_output(final_output)
    if format_type == "JSON_SCHEMA" and isinstance(normalized_json, (dict, list)):
        return [{
            "type": "JSON",
            "text": text_output or "",
            "json": normalized_json,
        }]
    return [{
        "type": "TEXT",
        "text": text_output or "",
        "json": {},
    }]


async def _run(payload: dict[str, Any]) -> dict[str, Any]:
    messages = payload.get("messages") or []
    instructions, transcript = _build_transcript(messages)
    instructions = _append_json_instruction(instructions, payload.get("responseFormat"))
    model = payload.get("model") or os.getenv("OPENAI_MODEL") or "gpt-5.5"
    enabled_tool_names, enabled_tools = _resolve_enabled_tools(payload)
    agent = Agent(
        name="AI Agent Provider",
        instructions=instructions or "Answer the user's request clearly and concisely.",
        model=model,
        tools=enabled_tools,
    )
    result = await Runner.run(agent, transcript or "USER: ")
    final_output = result.final_output
    outputs = _build_outputs(final_output, payload.get("responseFormat"))
    provider_meta = {
        "last_agent": getattr(getattr(result, "last_agent", None), "name", None),
        "raw_type": type(final_output).__name__,
        "enabled_tools": enabled_tool_names,
    }
    return {
        "requestId": str(uuid.uuid4()),
        "model": model,
        "finishReason": "STOP",
        "outputs": outputs,
        "usage": {
            "inputTokens": 0,
            "outputTokens": 0,
            "totalTokens": 0,
        },
        "providerMeta": provider_meta,
    }


def main() -> None:
    payload = json.load(sys.stdin)
    result = asyncio.run(_run(payload))
    json.dump(result, sys.stdout, ensure_ascii=False)


if __name__ == "__main__":
    main()
