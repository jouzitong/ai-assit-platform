from __future__ import annotations

import importlib.metadata
import inspect
import json
import os
from dataclasses import asdict, is_dataclass
from typing import Any

from ..agents.factory import AgentFactory, SdkGraph
from ..compiler import CompiledGraph
from ..events import EventEmitter, emit_sdk_event
from ..protocol import build_application_input


def build_sdk_graph(graph: CompiledGraph, emitter: EventEmitter) -> SdkGraph:
    """Build only the root now; specialist Agent instances are delegated lazily."""

    return AgentFactory(graph, emitter).build_root()


async def run_graph(graph: CompiledGraph, emitter: EventEmitter) -> dict[str, Any]:
    from agents import Runner

    sdk_graph = build_sdk_graph(graph, emitter)
    root = graph.root
    emitter.event(
        "agent.started",
        status="RUNNING",
        message=f"{root.name} started",
        agent=root,
        ext={"enabledTools": root.tool_names, "snapshotHash": graph.payload.get("snapshotHash")},
    )
    result = Runner.run_streamed(
        sdk_graph.root,
        build_application_input(
            graph.payload.get("messages"),
            graph.payload["run"].get("input"),
            graph.payload["run"].get("context"),
        ),
        max_turns=graph.max_turns,
    )
    async for event in result.stream_events():
        emit_sdk_event(
            event,
            emitter,
            sdk_graph.compiled_for,
            lambda name: _gateway_tool_identity(graph, name),
        )

    final_output = result.final_output
    last_sdk_agent = getattr(result, "last_agent", None)
    last_agent = sdk_graph.compiled_for(last_sdk_agent) or root
    usage = extract_usage(result)
    normalized_output = _output_text(final_output)
    outputs = _build_outputs(final_output, graph.payload.get("responseFormat"))
    artifacts = extract_artifacts(final_output)
    for artifact in artifacts:
        emitter.event(
            "artifact.created",
            status="SUCCESS",
            message=f"Artifact {artifact['artifactCode']} created",
            agent=last_agent,
            ext={
                "artifactCode": artifact["artifactCode"],
                "artifactType": artifact["artifactType"],
                "contentFormat": artifact["contentFormat"],
            },
        )
    emitter.event(
        "agent.completed",
        status="SUCCESS",
        message=f"{last_agent.name} completed",
        agent=last_agent,
        ext={"usage": usage},
    )
    emitter.event(
        "round.completed",
        status="SUCCESS",
        message="Agent round completed",
        agent=last_agent,
        ext={"usage": usage, "artifactCount": len(artifacts)},
    )
    run = graph.payload["run"]
    return {
        "protocolVersion": "2.0",
        "runId": run.get("runId"),
        "requestId": run.get("requestId"),
        "model": _runtime_model(root.model),
        "finishReason": "STOP",
        "status": "SUCCESS",
        "finalOutput": normalized_output,
        "finalAgentCode": last_agent.code,
        "outputs": outputs,
        "usage": usage,
        "artifacts": artifacts,
        "providerMeta": {
            "runtimeType": "OPENAI_AGENTS_PYTHON",
            "sdkVersion": _sdk_version(),
            "protocolVersion": "2.0",
            "snapshotHash": graph.payload.get("snapshotHash"),
            "lastAgent": last_agent.code,
            "enabledTools": root.tool_names,
        },
    }


def extract_usage(result: Any) -> dict[str, int]:
    """Read actual SDK usage without assuming one historical result layout."""

    candidates = [
        getattr(getattr(result, "context_wrapper", None), "usage", None),
        getattr(getattr(result, "context", None), "usage", None),
        getattr(result, "usage", None),
    ]
    for candidate in candidates:
        usage = _usage_value(candidate)
        if usage is not None:
            return usage

    total = {"inputTokens": 0, "outputTokens": 0, "totalTokens": 0}
    found = False
    for response in getattr(result, "raw_responses", None) or []:
        usage = _usage_value(getattr(response, "usage", None))
        if usage is None:
            continue
        found = True
        total["inputTokens"] += usage["inputTokens"]
        total["outputTokens"] += usage["outputTokens"]
        total["totalTokens"] += usage["totalTokens"]
    return total if found else {"inputTokens": 0, "outputTokens": 0, "totalTokens": 0}


def _usage_value(value: Any) -> dict[str, int] | None:
    if value is None:
        return None
    input_tokens = _int_attr(value, "input_tokens", "inputTokens", "prompt_tokens")
    output_tokens = _int_attr(value, "output_tokens", "outputTokens", "completion_tokens")
    total_tokens = _int_attr(value, "total_tokens", "totalTokens")
    if input_tokens is None and output_tokens is None and total_tokens is None:
        return None
    resolved_input = input_tokens or 0
    resolved_output = output_tokens or 0
    return {
        "inputTokens": resolved_input,
        "outputTokens": resolved_output,
        "totalTokens": total_tokens if total_tokens is not None else resolved_input + resolved_output,
    }


def _int_attr(value: Any, *names: str) -> int | None:
    for name in names:
        candidate = value.get(name) if isinstance(value, dict) else getattr(value, name, None)
        if candidate is None:
            continue
        try:
            return int(candidate)
        except (TypeError, ValueError):
            continue
    return None


def _model_settings(model_settings_type: Any, values: dict[str, Any]) -> Any | None:
    if not values:
        return None
    aliases = {
        "topP": "top_p",
        "maxTokens": "max_tokens",
        "parallelToolCalls": "parallel_tool_calls",
        "toolChoice": "tool_choice",
        "frequencyPenalty": "frequency_penalty",
        "presencePenalty": "presence_penalty",
        "promptCacheRetention": "prompt_cache_retention",
        "includeUsage": "include_usage",
        "responseInclude": "response_include",
        "topLogprobs": "top_logprobs",
        "extraQuery": "extra_query",
        "extraBody": "extra_body",
        "extraHeaders": "extra_headers",
        "extraArgs": "extra_args",
        "contextManagement": "context_management",
        "promptCacheOptions": "prompt_cache_options",
    }
    normalized = {aliases.get(key, key): value for key, value in values.items() if value is not None}
    kwargs = _supported_kwargs(model_settings_type, normalized)
    return model_settings_type(**kwargs) if kwargs else None


def _supported_kwargs(callable_value: Any, kwargs: dict[str, Any]) -> dict[str, Any]:
    try:
        signature = inspect.signature(callable_value)
    except (TypeError, ValueError):
        return kwargs
    if any(parameter.kind == inspect.Parameter.VAR_KEYWORD for parameter in signature.parameters.values()):
        return kwargs
    return {key: value for key, value in kwargs.items() if key in signature.parameters and value is not None}


def _runtime_model(value: str | None) -> str:
    configured = (os.getenv("OPENAI_MODEL") or "").strip()
    if configured:
        return configured
    model = str(value or "").strip()
    if model and "://" not in model:
        return model
    return "gpt-5.5"


def _output_text(value: Any) -> str:
    if isinstance(value, str):
        return value
    return json.dumps(value, ensure_ascii=False, default=str)


def _build_outputs(final_output: Any, response_format: Any) -> list[dict[str, Any]]:
    text = _output_text(final_output)
    format_type = str((response_format or {}).get("type") or "").upper() if isinstance(response_format, dict) else ""
    if format_type == "JSON_SCHEMA":
        decoded = _decode_json_value(final_output)
        if isinstance(decoded, (dict, list)):
            return [{"type": "JSON", "text": text, "json": decoded}]
    return [{"type": "TEXT", "text": text, "json": {}}]


def extract_artifacts(final_output: Any) -> list[dict[str, Any]]:
    decoded = _decode_json_value(final_output)
    if not isinstance(decoded, dict) or not isinstance(decoded.get("artifacts"), list):
        return []

    normalized: list[dict[str, Any]] = []
    for item in decoded["artifacts"]:
        if not isinstance(item, dict):
            continue
        code = _first_text(item.get("artifactCode"), item.get("code"))
        if not code:
            continue
        content = item.get("content")
        if "content" not in item:
            content = _first_value(item, "value", "data", "json", "text")
        artifact_type = _first_text(item.get("artifactType"), item.get("type")) or "AGENT_OUTPUT"
        content_format = _first_text(item.get("contentFormat"), item.get("format"))
        if not content_format:
            content_format = "JSON" if isinstance(content, (dict, list)) else "PLAIN_TEXT"
        artifact = dict(item)
        artifact.pop("code", None)
        artifact.pop("type", None)
        artifact.pop("format", None)
        artifact.update(
            {
                "artifactCode": code,
                "artifactType": artifact_type,
                "contentFormat": content_format,
                "content": content,
            }
        )
        normalized.append(artifact)
    return normalized


def _decode_json_value(value: Any) -> Any:
    """Decode only a bounded, complete JSON value (optionally one markdown fence)."""

    if isinstance(value, (dict, list)):
        return value
    model_dump = getattr(value, "model_dump", None)
    if callable(model_dump):
        try:
            signature = inspect.signature(model_dump)
            parameters = signature.parameters.values()
            accepts_mode = "mode" in signature.parameters or any(
                parameter.kind == inspect.Parameter.VAR_KEYWORD for parameter in parameters
            )
        except (TypeError, ValueError):
            accepts_mode = True
        dumped = model_dump(mode="json") if accepts_mode else model_dump()
        return dumped if isinstance(dumped, (dict, list)) else None
    if is_dataclass(value) and not isinstance(value, type):
        dumped = asdict(value)
        return dumped if isinstance(dumped, (dict, list)) else None
    if not isinstance(value, str):
        return None
    text = value.strip()
    if text.startswith("```") and text.endswith("```"):
        lines = text.splitlines()
        if len(lines) < 3 or not lines[-1].strip() == "```":
            return None
        text = "\n".join(lines[1:-1]).strip()
    if not text or text[0] not in "{[" or len(text.encode("utf-8")) > 4 * 1024 * 1024:
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return None


def _first_text(*values: Any) -> str | None:
    for value in values:
        if value is None:
            continue
        text = str(value).strip()
        if text:
            return text
    return None


def _first_value(value: dict[str, Any], *keys: str) -> Any:
    for key in keys:
        if key in value:
            return value[key]
    return None


def _safe_identifier(value: str) -> str:
    normalized = "".join(character.lower() if character.isalnum() else "_" for character in value)
    return normalized.strip("_") or "agent"


def _sdk_version() -> str:
    try:
        return importlib.metadata.version("openai-agents")
    except importlib.metadata.PackageNotFoundError:
        return "unknown"


def _gateway_tool_identity(graph: CompiledGraph, sdk_name: str | None) -> dict[str, Any] | None:
    if not sdk_name:
        return None
    for descriptor in graph.gateway_tools.values():
        if descriptor.get("sdkName") == sdk_name:
            return descriptor
    return None
