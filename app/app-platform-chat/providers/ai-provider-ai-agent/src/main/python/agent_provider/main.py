import asyncio
import json
import os
import sys
import uuid
from typing import Any

from agents import Agent, Runner

from tools import (
    data_format_validate_tool,
    knowledge_base_search_tool,
    render_json_validate_tool,
    web_search_tool,
)


TOOL_REGISTRY = {
    "data_format_validate_tool": data_format_validate_tool,
    "knowledge_base_search_tool": knowledge_base_search_tool,
    "render_json_validate_tool": render_json_validate_tool,
    "web_search_tool": web_search_tool,
}


def _emit(frame: dict[str, Any]) -> None:
    sys.stdout.write(json.dumps(frame, ensure_ascii=False) + "\n")
    sys.stdout.flush()


def _item_ext(item: Any, event_name: str) -> dict[str, Any]:
    raw_item = getattr(item, "raw_item", None)
    tool_name = (
        getattr(raw_item, "name", None)
        or getattr(item, "name", None)
        or getattr(raw_item, "type", None)
    )
    call_id = getattr(raw_item, "call_id", None) or getattr(item, "call_id", None)
    return {
        "activity": event_name,
        "toolName": tool_name,
        "callId": call_id,
        "itemType": getattr(item, "type", None),
    }


def _emit_run_item_event(event: Any) -> None:
    name = str(getattr(event, "name", "") or "")
    if name in {"tool_called", "tool_search_called"}:
        _emit({
            "type": "activity",
            "source": "AI_AGENT",
            "phase": "RUNNING",
            "status": "RUNNING",
            "message": "AI Agent 正在调用工具",
            "ext": _item_ext(event.item, name),
        })
    elif name in {"tool_output", "tool_search_output_created"}:
        _emit({
            "type": "activity",
            "source": "AI_AGENT",
            "phase": "COMPLETED",
            "status": "SUCCESS",
            "message": "AI Agent 工具调用完成",
            "ext": _item_ext(event.item, name),
        })
    elif name == "handoff_requested":
        _emit({
            "type": "activity",
            "source": "AI_AGENT",
            "phase": "RUNNING",
            "status": "RUNNING",
            "message": "AI Agent 正在切换执行角色",
            "ext": _item_ext(event.item, name),
        })
    elif name == "handoff_occured":
        _emit({
            "type": "activity",
            "source": "AI_AGENT",
            "phase": "COMPLETED",
            "status": "SUCCESS",
            "message": "AI Agent 执行角色切换完成",
            "ext": _item_ext(event.item, name),
        })


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
    _emit({
        "type": "activity",
        "source": "AI_AGENT",
        "phase": "STARTED",
        "status": "RUNNING",
        "message": "AI Agent 已开始执行",
        "ext": {"activity": "agent_run", "enabledTools": enabled_tool_names},
    })
    agent = Agent(
        name="AI Agent Provider",
        instructions=instructions or "Answer the user's request clearly and concisely.",
        model=model,
        tools=enabled_tools,
    )
    _emit({
        "type": "activity",
        "source": "AI_AGENT",
        "phase": "RUNNING",
        "status": "RUNNING",
        "message": "AI Agent 正在推理并调用工具",
        "ext": {"activity": "agent_run", "enabledTools": enabled_tool_names},
    })
    result = Runner.run_streamed(agent, transcript or "USER: ")
    async for event in result.stream_events():
        event_type = getattr(event, "type", None)
        if event_type == "raw_response_event":
            data = getattr(event, "data", None)
            if getattr(data, "type", None) == "response.output_text.delta":
                delta = getattr(data, "delta", None)
                if delta:
                    _emit({"type": "delta", "delta": delta})
        elif event_type == "run_item_stream_event":
            _emit_run_item_event(event)
        elif event_type == "agent_updated_stream_event":
            agent_name = getattr(getattr(event, "new_agent", None), "name", None)
            _emit({
                "type": "activity",
                "source": "AI_AGENT",
                "phase": "RUNNING",
                "status": "RUNNING",
                "message": "AI Agent 已切换执行角色",
                "ext": {"activity": "agent_updated", "agentName": agent_name},
            })
    final_output = result.final_output
    outputs = _build_outputs(final_output, payload.get("responseFormat"))
    provider_meta = {
        "last_agent": getattr(getattr(result, "last_agent", None), "name", None),
        "raw_type": type(final_output).__name__,
        "enabled_tools": enabled_tool_names,
    }
    response = {
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
    _emit({
        "type": "activity",
        "source": "AI_AGENT",
        "phase": "COMPLETED",
        "status": "SUCCESS",
        "message": "AI Agent 执行完成",
        "ext": {"activity": "agent_run", "enabledTools": enabled_tool_names},
    })
    return response


def main() -> None:
    try:
        payload = json.load(sys.stdin)
        result = asyncio.run(_run(payload))
        _emit({"type": "result", "data": result})
    except Exception as exc:
        _emit({
            "type": "error",
            "source": "AI_AGENT",
            "phase": "FAILED",
            "status": "FAILED",
            "message": str(exc),
        })
        raise


if __name__ == "__main__":
    main()
