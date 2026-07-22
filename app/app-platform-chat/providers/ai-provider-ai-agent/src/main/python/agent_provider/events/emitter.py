from __future__ import annotations

import json
import re
import sys
import threading
from datetime import datetime, timezone
from typing import Any, Callable


class EventEmitter:
    """Emits the platform event contract without leaking SDK-specific objects."""

    def __init__(
        self,
        payload: dict[str, Any],
        writer: Callable[[dict[str, Any]], None] | None = None,
    ) -> None:
        run = payload.get("run") if isinstance(payload.get("run"), dict) else {}
        self._base = {
            "protocolVersion": "2.0",
            "runId": run.get("runId"),
            "requestId": run.get("requestId"),
            "traceId": run.get("traceId"),
            "sessionCode": run.get("sessionCode"),
            "roundCode": run.get("roundCode"),
        }
        self._writer = writer or _stdout_writer
        self._lock = threading.Lock()

    def event(
        self,
        event_type: str,
        *,
        status: str | None = None,
        message: str | None = None,
        delta: str | None = None,
        agent: Any = None,
        ext: dict[str, Any] | None = None,
        frame_type: str = "event",
    ) -> dict[str, Any]:
        frame: dict[str, Any] = {
            **self._base,
            "type": frame_type,
            "eventType": event_type,
            "status": status,
            "message": message,
            "delta": delta,
            "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "source": "OPENAI_AGENTS_PYTHON",
            "ext": _clean_ext(ext or {}),
        }
        identity = _agent_identity(agent)
        frame.update({key: value for key, value in identity.items() if value is not None})
        with self._lock:
            self._writer(frame)
        return frame

    def result(self, data: dict[str, Any]) -> None:
        with self._lock:
            self._writer({"protocolVersion": "2.0", "type": "result", "data": data})


def emit_sdk_event(
    event: Any,
    emitter: EventEmitter,
    agent_lookup: Callable[[Any], Any],
    tool_lookup: Callable[[str | None], dict[str, Any] | None] | None = None,
    hidden_agent_codes: set[str] | None = None,
    emit_output_deltas: bool = True,
    mapped_event_observer: Callable[[str, dict[str, Any], Any], None] | None = None,
) -> None:
    event_type = str(getattr(event, "type", "") or "")
    if event_type == "raw_response_event":
        data = getattr(event, "data", None)
        if getattr(data, "type", None) == "response.output_text.delta":
            delta = getattr(data, "delta", None)
            if delta and emit_output_deltas:
                emitter.event("assistant.message.delta", status="RUNNING", delta=str(delta))
        return
    if event_type == "agent_updated_stream_event":
        new_agent = getattr(event, "new_agent", None)
        compiled = agent_lookup(new_agent)
        if compiled is not None and compiled.code in (hidden_agent_codes or set()):
            return
        agent_name = getattr(new_agent, "name", None)
        display_name = f"“{agent_name}”" if agent_name else "另一个"
        emitter.event(
            "agent.changed",
            status="RUNNING",
            message=f"已切换至{display_name}智能体执行",
            agent=compiled or new_agent,
        )
        return
    if event_type != "run_item_stream_event":
        return
    mapped = map_run_item_event(event, tool_lookup)
    if mapped is None:
        return
    platform_type, status, message, ext = mapped
    item = getattr(event, "item", None)
    sdk_agent = (
        getattr(event, "agent", None)
        or getattr(item, "agent", None)
        or getattr(item, "source_agent", None)
    )
    emitter.event(
        platform_type,
        status=status,
        message=message,
        agent=agent_lookup(sdk_agent),
        ext=ext,
    )
    if mapped_event_observer is not None:
        try:
            mapped_event_observer(platform_type, ext, item)
        except Exception:
            # Evidence collection is an optional guardrail input and must not
            # interrupt the Agent event stream.
            pass


def map_run_item_event(
    event: Any,
    tool_lookup: Callable[[str | None], dict[str, Any] | None] | None = None,
) -> tuple[str, str, str, dict[str, Any]] | None:
    name = str(getattr(event, "name", "") or "")
    item = getattr(event, "item", None)
    ext = _item_ext(item, name, tool_lookup)
    if name in {"tool_called", "tool_search_called"}:
        return "tool.started", "RUNNING", "开始调用工具", ext
    if name in {"tool_output", "tool_search_output_created"}:
        failed = _is_failed_tool_output(item)
        return (
            "tool.failed" if failed else "tool.completed",
            "FAILED" if failed else "SUCCESS",
            "工具调用失败" if failed else "工具调用完成",
            ext,
        )
    if name == "handoff_requested":
        return "handoff.requested", "RUNNING", "正在将任务交接给协作智能体", ext
    if name in {"handoff_occured", "handoff_occurred"}:
        return "handoff.completed", "SUCCESS", "协作智能体交接完成", ext
    return None


def _item_ext(
    item: Any,
    event_name: str,
    tool_lookup: Callable[[str | None], dict[str, Any] | None] | None,
) -> dict[str, Any]:
    raw_item = getattr(item, "raw_item", None)
    tool_name = (
        getattr(raw_item, "name", None)
        or getattr(item, "name", None)
        or getattr(raw_item, "type", None)
    )
    if _is_generic_output_type(tool_name):
        tool_name = None
    call_id = getattr(raw_item, "call_id", None) or getattr(item, "call_id", None)
    gateway_identity = tool_lookup(str(tool_name) if tool_name else None) if tool_lookup else None
    platform_tool_code = gateway_identity.get("code") if gateway_identity else tool_name
    display_name = gateway_identity.get("name") if gateway_identity else platform_tool_code
    ext: dict[str, Any] = {
        "activityCode": call_id or _fallback_activity_code(event_name, platform_tool_code),
        "activityType": "TOOL_CALL" if "tool" in event_name else "AGENT_HANDOFF",
        "toolCode": platform_tool_code,
        "callId": call_id,
    }
    if "tool" in event_name and display_name:
        ext["activityName"] = f"调用工具：{display_name}"
    elif "handoff" in event_name:
        ext["activityName"] = "智能体协作交接"
    if gateway_identity:
        ext["toolVersion"] = gateway_identity.get("version")
    input_summary = _summary(raw_item, "arguments", "input")
    output_summary = _summary(item, "output", "result") or _summary(raw_item, "output", "result")
    if input_summary:
        ext["inputSummary"] = input_summary
    if output_summary:
        ext["outputSummary"] = output_summary
    return ext


def _fallback_activity_code(event_name: str, tool_code: Any) -> str:
    """Keep lifecycle events mergeable even when an SDK item omits call_id."""

    identity = str(tool_code or "agent")
    if "tool" in event_name:
        return f"tool:{identity}"
    if "handoff" in event_name:
        return f"handoff:{identity}"
    return f"activity:{identity}"


def _is_generic_output_type(value: Any) -> bool:
    normalized = str(value or "").lower()
    return normalized.endswith("_output") or normalized in {"function_call_output", "tool_output"}


def _agent_identity(agent: Any) -> dict[str, Any]:
    if agent is None:
        return {}
    if isinstance(agent, dict):
        return {
            "agentCode": agent.get("code"),
            "agentVersion": agent.get("version"),
            "agentName": agent.get("name"),
        }
    return {
        "agentCode": getattr(agent, "code", None),
        "agentVersion": getattr(agent, "version", None),
        "agentName": getattr(agent, "name", None),
    }


def _is_failed_tool_output(item: Any) -> bool:
    output = getattr(item, "output", None)
    if isinstance(output, dict):
        status = str(output.get("status") or "").upper()
        return (
            output.get("success") is False
            or output.get("valid") is False and bool(output.get("errors"))
            or status in {"FAILED", "ERROR", "CANCELLED"}
        )
    if isinstance(output, str):
        try:
            decoded = json.loads(output)
        except (json.JSONDecodeError, TypeError):
            return False
        if not isinstance(decoded, dict):
            return False
        status = str(decoded.get("status") or "").upper()
        return (
            decoded.get("success") is False
            or decoded.get("valid") is False and bool(decoded.get("errors"))
            or status in {"FAILED", "ERROR", "CANCELLED"}
        )
    return False


def _summary(value: Any, *attributes: str) -> str | None:
    for attribute in attributes:
        candidate = getattr(value, attribute, None)
        if candidate is None:
            continue
        if isinstance(candidate, str):
            text = candidate.strip()
        else:
            try:
                text = json.dumps(candidate, ensure_ascii=False, default=str)
            except TypeError:
                text = str(candidate)
        if text:
            text = re.sub(r"(?i)bearer\s+[^\s,;]+", "Bearer [REDACTED]", text)
            text = re.sub(r"\bsk-[A-Za-z0-9_-]{8,}\b", "[REDACTED_OPENAI_KEY]", text)
            text = re.sub(
                r'''(?i)(["']?(?:authorization|api[ _-]?key|secret|password|access[_-]?token|'''
                r'''refresh[_-]?token|token)["']?\s*[:=]\s*["']?)([^"'\s,;}]+)''',
                r"\1[REDACTED]",
                text,
            )
            return text[:1000]
    return None


def _clean_ext(value: dict[str, Any]) -> dict[str, Any]:
    cleaned: dict[str, Any] = {}
    for key, item in value.items():
        if item is None:
            continue
        if isinstance(item, (str, int, float, bool, list, dict)):
            cleaned[key] = item
        else:
            cleaned[key] = str(item)
    return cleaned


def _stdout_writer(frame: dict[str, Any]) -> None:
    sys.stdout.write(json.dumps(frame, ensure_ascii=False, separators=(",", ":")) + "\n")
    sys.stdout.flush()
