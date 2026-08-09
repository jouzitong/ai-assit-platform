from __future__ import annotations

import importlib.metadata
import inspect
import json
import os
from typing import Any

from ..agents.factory import AgentFactory, SdkGraph
from ..artifacts import (
    combine_event_observers,
    decode_json_value,
    extract_artifacts,
    extract_model_artifacts,
    merge_authoritative_artifacts,
    merge_artifacts,
)
from ..capability_identity import runtime_tool_identity
from ..compiler import CompiledGraph
from ..events import EventEmitter, emit_sdk_event
from ..protocol import build_application_input
from .confidence_guard import ConfidencePolicy, KnowledgeEvidenceCollector, guard_output
from .request_analysis import (
    RENDER_APPLICATION_AGENT_CODE,
    RequestAnalysis,
    analyze_request,
    requires_render_application,
    safe_audit_text,
)


RENDER_DOCUMENT_WORKFLOW_REF = "workflow://render-document-delivery/v1"


def build_sdk_graph(graph: CompiledGraph, emitter: EventEmitter) -> SdkGraph:
    """Build only the root now; specialist Agent instances are delegated lazily."""

    return AgentFactory(graph, emitter).build_root()


async def run_graph(graph: CompiledGraph, emitter: EventEmitter) -> dict[str, Any]:
    from agents import Runner

    sdk_graph = build_sdk_graph(graph, emitter)
    root = graph.root
    confidence_policy = ConfidencePolicy.from_payload(graph.payload)
    analysis_activity_code = _attempt_activity_code(graph, "main-agent-request-analysis")
    _emit_execution_note(
        emitter,
        event_type="thinking.analysis.started",
        status="RUNNING",
        activity_code=analysis_activity_code,
        activity_name="分析用户请求",
        input_summary=_request_analysis_summary(graph),
    )
    request_analysis = await analyze_request(
        graph,
        _latest_user_request(graph) or "Continue.",
        model=_runtime_model(root.model),
    )
    execution_sdk_agent, execution_agent, route_applied, route_source = _execution_route(
        graph,
        sdk_graph,
        request_analysis,
    )
    artifact_transport = execution_agent.code == RENDER_APPLICATION_AGENT_CODE
    analysis_ext = request_analysis.event_ext(graph)
    analysis_ext.update(
        {
            "routeApplied": route_applied,
            "selectedAgentCode": execution_agent.code,
            "routeSource": route_source,
            "routeNature": "APPLIED" if route_applied else analysis_ext.get("routeNature"),
        }
    )
    _emit_execution_note(
        emitter,
        event_type="thinking.analysis.completed",
        status="SUCCESS",
        activity_code=analysis_activity_code,
        activity_name="分析用户请求",
        output_summary=request_analysis.output_summary(graph),
        ext=analysis_ext,
    )
    application_input = build_application_input(
        graph.payload.get("messages"),
        graph.payload["run"].get("input"),
        graph.payload["run"].get("context"),
    )
    result = Runner.run_streamed(
        execution_sdk_agent,
        application_input,
        max_turns=graph.max_turns,
    )
    evidence_collector = KnowledgeEvidenceCollector()
    artifact_collector = getattr(sdk_graph, "artifact_collector", None)
    mapped_event_observer = combine_event_observers(
        evidence_collector.observe,
        getattr(artifact_collector, "observe", None),
    )
    async for event in result.stream_events():
        emit_sdk_event(
            event,
            emitter,
            sdk_graph.compiled_for,
            lambda name: runtime_tool_identity(graph, name),
            hidden_agent_codes={root.code},
            # A directly routed builder returns an artifact transport envelope,
            # not chat prose. Never stream that JSON into the conversation.
            emit_output_deltas=not confidence_policy.requires_guard and not artifact_transport,
            mapped_event_observer=mapped_event_observer,
        )

    final_output = result.final_output
    last_sdk_agent = getattr(result, "last_agent", None)
    last_agent = sdk_graph.compiled_for(last_sdk_agent) or execution_agent
    usage = _merge_usage(request_analysis.usage, extract_usage(result))
    guarded_output = await guard_output(
        sdk_agent=last_sdk_agent or execution_sdk_agent,
        compiled_agent=last_agent,
        graph=graph,
        emitter=emitter,
        original_task=_latest_user_request(graph) or "Continue.",
        initial_output=final_output,
        policy=confidence_policy,
        initial_evidence=evidence_collector.evidence,
    )
    guarded_transport_output = guarded_output.text
    if route_applied:
        _collect_specialist_outputs(sdk_graph, final_output, guarded_transport_output)
    artifacts = _merge_run_artifacts(
        sdk_graph,
        final_output,
        guarded_transport_output,
    )
    normalized_output = _user_visible_final_output(
        guarded_transport_output,
        artifacts,
        artifact_transport=artifact_transport,
    )
    _emit_execution_note(
        emitter,
        event_type="thinking.conclusion.completed",
        status="SUCCESS",
        activity_code=_attempt_activity_code(graph, "main-agent-conclusion"),
        activity_name="形成处理结论",
        output_summary=_conclusion_summary(normalized_output),
        ext={"usage": usage},
    )
    outputs = _build_outputs(normalized_output, graph.payload.get("responseFormat"))
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
    run = graph.payload["run"]
    return {
        "protocolVersion": "2.0",
        "runId": run.get("runId"),
        "requestId": run.get("requestId"),
        "model": _runtime_model(execution_agent.model),
        "finishReason": "STOP",
        "status": _result_status(request_analysis, route_applied=route_applied),
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
            "enabledTools": execution_agent.tool_names,
            "requestAnalysis": {
                "status": request_analysis.status,
                "durationMs": request_analysis.duration_ms,
                "usage": request_analysis.usage,
                "routeApplied": route_applied,
                "selectedAgentCode": execution_agent.code,
                "routeSource": route_source,
            },
            "confidence": guarded_output.audit_dict() if confidence_policy.requires_guard else {"enabled": False},
        },
    }


def _result_status(
    request_analysis: Any,
    *,
    route_applied: bool = False,
) -> str:
    """Mark only a validated request for more user input as non-deliverable."""

    # A trusted Render workflow/route is already under fail-closed artifact
    # acceptance. Its repair turns must not downgrade that contract merely
    # because request analysis asks for more input.
    if route_applied:
        return "SUCCESS"
    if not isinstance(request_analysis, RequestAnalysis):
        return "SUCCESS"
    if request_analysis.route_validated is not True:
        return "SUCCESS"
    if request_analysis.route.mode == "CLARIFY":
        return "INPUT_REQUIRED"
    asks_user = any(
        remediation.action == "ASK_USER"
        for remediation in request_analysis.low_readiness_remediation
    )
    if (
        request_analysis.route.mode != "DELEGATE"
        and request_analysis.execution_readiness.level == "LOW"
        and asks_user
    ):
        return "INPUT_REQUIRED"
    return "SUCCESS"


def _execution_route(
    graph: CompiledGraph,
    sdk_graph: SdkGraph,
    request_analysis: Any,
) -> tuple[Any, Any, bool, str]:
    """Apply only the proof-bound render route; all other requests keep the root flow."""

    workflow_requires_render = _requires_render_document_workflow(graph.payload)
    route = getattr(request_analysis, "route", None)
    analysis_selected_render = (
        isinstance(request_analysis, RequestAnalysis)
        and request_analysis.route_validated is True
        and _requires_render_route(graph)
        and str(getattr(route, "mode", "")).upper() == "DELEGATE"
        and str(getattr(route, "agent_code", "")).strip() == RENDER_APPLICATION_AGENT_CODE
    )
    explicit_builder_ready = (
        isinstance(request_analysis, RequestAnalysis)
        and request_analysis.route_validated is True
        and graph.root.code == RENDER_APPLICATION_AGENT_CODE
        and request_analysis.execution_readiness.level == "READY"
        and request_analysis.route.mode != "CLARIFY"
    )
    if (
        not workflow_requires_render
        and not analysis_selected_render
        and not explicit_builder_ready
    ):
        return sdk_graph.root, graph.root, False, "ROOT"

    target_key = _reachable_agent_key(graph, RENDER_APPLICATION_AGENT_CODE)
    if target_key is None:
        raise ValueError(
            "Render document delivery requires the dashboard-application-builder Agent"
        )
    specialist = sdk_graph.agent_for_key(target_key)
    if specialist is None:
        raise ValueError(
            "Render document delivery could not build the dashboard-application-builder Agent"
        )
    if workflow_requires_render:
        source = "WORKFLOW_SNAPSHOT"
    elif analysis_selected_render:
        source = "REQUEST_ANALYSIS"
    else:
        source = "EXPLICIT_TARGET_READINESS"
    return specialist, graph.agents[target_key], True, source


def _requires_render_document_workflow(payload: Any) -> bool:
    """Recognize the server-issued v1 RenderDocument delivery contract exactly."""

    if not isinstance(payload, dict):
        return False
    workflow = payload.get("workflowSnapshot")
    if not isinstance(workflow, dict):
        return False
    if workflow.get("workflowRef") != RENDER_DOCUMENT_WORKFLOW_REF:
        return False
    if workflow.get("apiVersion") != "ai.platform/v1alpha1":
        return False
    if workflow.get("kind") != "ArtifactWorkflow":
        return False
    metadata = workflow.get("metadata")
    if not isinstance(metadata, dict):
        return False
    if metadata.get("code") != "render-document-delivery" or metadata.get("version") != 1:
        return False
    specification = workflow.get("spec")
    if not isinstance(specification, dict):
        return False
    completion = specification.get("completionPolicy")
    if not isinstance(completion, dict):
        return False
    if completion.get("requireAllRequiredArtifacts") is not True:
        return False
    if completion.get("requireAllBlockingChecksPassed") is not True:
        return False
    repair = specification.get("repairPolicy")
    if not isinstance(repair, dict):
        return False
    if repair.get("maxRepairAttempts") != 1 or repair.get("onExhausted") != "FAILED":
        return False
    artifacts = specification.get("artifacts")
    if not isinstance(artifacts, list):
        return False
    final_answer = next(
        (
            artifact
            for artifact in artifacts
            if _matches_required_artifact(artifact, "final-answer", "TEXT", "MARKDOWN")
        ),
        None,
    )
    data_preview = next(
        (
            artifact
            for artifact in artifacts
            if _matches_required_artifact(artifact, "data-preview", "JSON", "JSON")
        ),
        None,
    )
    render_document = next(
        (
            artifact
            for artifact in artifacts
            if _matches_required_artifact(artifact, "render-document", "RENDER_JSON", "JSON")
        ),
        None,
    )
    validation_report = next(
        (
            artifact
            for artifact in artifacts
            if _matches_required_artifact(artifact, "validation-report", "JSON", "JSON")
        ),
        None,
    )
    if any(
        artifact is None
        for artifact in (final_answer, data_preview, render_document, validation_report)
    ):
        return False
    final_schema = final_answer.get("inlineSchema")
    data_schema = data_preview.get("inlineSchema")
    render_schema = render_document.get("inlineSchema")
    validation_schema = validation_report.get("inlineSchema")
    return (
        isinstance(final_schema, dict)
        and final_schema.get("type") == "string"
        and _matches_data_preview_schema(data_schema)
        and _matches_render_document_schema(render_schema)
        and _matches_validation_report_schema(validation_schema)
    )


def _matches_required_artifact(
    artifact: Any,
    code: str,
    artifact_type: str,
    content_format: str,
) -> bool:
    return (
        isinstance(artifact, dict)
        and artifact.get("code") == code
        and artifact.get("artifactType") == artifact_type
        and artifact.get("contentFormat") == content_format
        and artifact.get("required") is True
    )


def _matches_data_preview_schema(schema: Any) -> bool:
    return (
        _object_schema_requires(
            schema,
            {
                "tool",
                "success",
                "model",
                "catalogVersion",
                "sourceRevision",
                "columns",
                "records",
            },
        )
        and _schema_property(schema, "tool", "string", ["data_preview_query_tool"])
        and _schema_property(schema, "success", "boolean", [True])
        and _schema_property(schema, "model", "string")
        and _schema_property(schema, "catalogVersion", "integer")
        and _schema_property(schema, "sourceRevision", "string")
        and _schema_property(schema, "columns", "array")
        and _schema_property(schema, "records", "array")
    )


def _matches_render_document_schema(schema: Any) -> bool:
    return (
        _object_schema_requires(schema, {"protocol", "protocolVersion", "pageId", "root"})
        and _schema_property(schema, "protocol", "string", ["render-json"])
        and _schema_property(schema, "protocolVersion", "string", ["1.0", "1.0.0"])
        and _schema_property(schema, "pageId", "string")
        and _schema_property(schema, "root", "object")
    )


def _matches_validation_report_schema(schema: Any) -> bool:
    return (
        _object_schema_requires(schema, {"tool", "valid"})
        and _schema_property(schema, "tool", "string", ["render_json_validate_tool"])
        and _schema_property(schema, "valid", "boolean", [True])
    )


def _object_schema_requires(schema: Any, required: set[str]) -> bool:
    if not isinstance(schema, dict) or schema.get("type") != "object":
        return False
    fields = schema.get("required")
    return (
        isinstance(fields, list)
        and all(isinstance(field, str) for field in fields)
        and required.issubset(set(fields))
        and isinstance(schema.get("properties"), dict)
    )


def _schema_property(
    schema: Any,
    name: str,
    expected_type: str,
    expected_enum: list[Any] | None = None,
) -> bool:
    properties = schema.get("properties") if isinstance(schema, dict) else None
    value = properties.get(name) if isinstance(properties, dict) else None
    if not isinstance(value, dict) or value.get("type") != expected_type:
        return False
    return expected_enum is None or value.get("enum") == expected_enum


def _reachable_agent_key(graph: CompiledGraph, agent_code: str) -> str | None:
    visited: set[str] = set()

    def visit(key: str, depth: int) -> str | None:
        if key in visited or key not in graph.agents or depth > graph.max_depth:
            return None
        visited.add(key)
        agent = graph.agents[key]
        if agent.code == agent_code:
            return key
        for link in [*agent.agent_tools, *agent.handoffs]:
            matched = visit(link.target_key, depth + 1)
            if matched is not None:
                return matched
        return None

    return visit(graph.root_key, 1)


def _collect_specialist_outputs(
    sdk_graph: SdkGraph,
    raw_output: Any,
    guarded_output: Any,
) -> None:
    collector = getattr(sdk_graph, "artifact_collector", None)
    collect_output = getattr(collector, "collect_output", None)
    if not callable(collect_output):
        return
    collect_output(raw_output)
    collect_output(guarded_output)


def _emit_execution_note(
    emitter: EventEmitter,
    *,
    event_type: str,
    status: str,
    activity_code: str,
    activity_name: str,
    input_summary: str | None = None,
    output_summary: str | None = None,
    ext: dict[str, Any] | None = None,
) -> None:
    """Emit an auditable execution summary, never the model's private reasoning."""

    detail = {
        "activityCode": activity_code,
        "activityType": "THINKING",
        "activityName": activity_name,
        "inputSummary": input_summary,
        "outputSummary": output_summary,
        **(ext or {}),
    }
    emitter.event(
        event_type,
        status=status,
        message=activity_name,
        ext=detail,
    )


def _request_analysis_summary(graph: CompiledGraph) -> str:
    request = _latest_user_request(graph)
    if not request:
        return "识别本轮目标、可用上下文以及是否需要知识库、工具或专业 Agent 协作。"
    safe_request = safe_audit_text(request, 360, "用户请求已接收")
    return (
        f"收到用户请求：{safe_request}。"
        "将识别任务目标，并判断是否需要知识库、工具或专业 Agent 协作。"
    )


def _attempt_activity_code(graph: CompiledGraph, prefix: str) -> str:
    run = graph.payload.get("run") if isinstance(graph.payload.get("run"), dict) else {}
    context = run.get("context") if isinstance(run.get("context"), dict) else {}
    attempt = context.get("executionAttempt", 1)
    normalized = "".join(
        character if character.isalnum() or character in {"-", "_"} else "-"
        for character in str(attempt).strip()
    ).strip("-")
    return f"{prefix}:{(normalized or '1')[:48]}"


def _latest_user_request(graph: CompiledGraph) -> str | None:
    run_input = graph.payload.get("run", {}).get("input")
    if isinstance(run_input, str) and run_input.strip():
        return run_input.strip()
    messages = graph.payload.get("messages")
    if not isinstance(messages, list):
        return None
    for message in reversed(messages):
        if not isinstance(message, dict) or str(message.get("role", "")).lower() != "user":
            continue
        content = message.get("content") or message.get("text")
        if isinstance(content, str) and content.strip():
            return content.strip()
    return None


def _render_request_context(graph: CompiledGraph) -> str:
    """Combine at most two user turns for a validated continuation route."""

    current = _latest_user_request(graph) or ""
    selected = [current[:1_200]] if current else []
    skipped_current = False
    messages = graph.payload.get("messages")
    if isinstance(messages, list):
        for message in reversed(messages):
            if not isinstance(message, dict) or str(message.get("role", "")).lower() != "user":
                continue
            content = message.get("content") or message.get("text")
            if not isinstance(content, str) or not content.strip():
                continue
            normalized = content.strip()
            if current and normalized == current and not skipped_current:
                skipped_current = True
                continue
            selected.append(normalized[:1_200])
            break
    return "\n".join(reversed(selected))


def _requires_render_route(graph: CompiledGraph) -> bool:
    current = _latest_user_request(graph) or ""
    return requires_render_application(current) or requires_render_application(
        _render_request_context(graph)
    )


def _conclusion_summary(final_output: str) -> str:
    if not final_output.strip():
        return "本轮处理完成，但没有生成可展示的文本结论。"
    return f"结论摘要：{_compact_summary(final_output, 600)}"


def _compact_summary(value: str, limit: int) -> str:
    """Normalize a summary without destroying Markdown block boundaries.

    Activity summaries are rendered as Markdown by the client.  Collapsing all
    whitespace (the previous implementation) turns tables, lists, and fenced
    blocks into one line, so normalize only line endings and surrounding
    whitespace.  Keep the character limit as a hard upper bound, including
    the truncation marker.
    """

    if limit <= 0:
        return ""

    normalized = value.replace("\r\n", "\n").replace("\r", "\n").strip()
    if len(normalized) <= limit:
        return normalized
    if limit == 1:
        return "…"
    return f"{normalized[:limit - 1]}…"


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


def _merge_usage(*values: dict[str, int]) -> dict[str, int]:
    merged = {"inputTokens": 0, "outputTokens": 0, "totalTokens": 0}
    for value in values:
        for key in merged:
            try:
                merged[key] += int((value or {}).get(key) or 0)
            except (TypeError, ValueError):
                continue
    return merged


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
        decoded = decode_json_value(final_output)
        if isinstance(decoded, (dict, list)):
            return [{"type": "JSON", "text": text, "json": decoded}]
    return [{"type": "TEXT", "text": text, "json": {}}]


def _user_visible_final_output(
    guarded_output: Any,
    artifacts: list[dict[str, Any]],
    *,
    artifact_transport: bool,
) -> str:
    """Separate a builder's artifact transport envelope from visible prose."""

    text = _output_text(guarded_output)
    if not artifact_transport:
        return text
    decoded = decode_json_value(guarded_output)
    if not isinstance(decoded, dict) or not isinstance(decoded.get("artifacts"), list):
        return text

    by_code = {
        artifact.get("artifactCode"): artifact
        for artifact in artifacts
        if isinstance(artifact, dict)
    }
    required = {"data-preview", "render-document", "validation-report"}
    if not required.issubset(by_code):
        return (
            "本次未形成通过数据预览与渲染校验的可交付结果，"
            "请根据验收提示补充信息或重试。"
        )

    preview = by_code["data-preview"].get("content")
    preview_content = preview if isinstance(preview, dict) else {}
    model = preview_content.get("model")
    model_text = model.strip() if isinstance(model, str) else ""
    records = preview_content.get("records")
    row_count = len(records) if isinstance(records, list) else 0
    query_type = preview_content.get("queryType")
    delivery_name = "数据列表" if query_type == "LIST" else "数据展示"
    subject = f" `{model_text}` 的" if model_text else ""
    return f"已生成并校验{subject}{delivery_name}（预览 {row_count} 条记录），可在下方查看。"


def _merge_run_artifacts(
    sdk_graph: SdkGraph,
    raw_final_output: Any,
    guarded_final_output: Any,
) -> list[dict[str, Any]]:
    collector = getattr(sdk_graph, "artifact_collector", None)
    snapshot = getattr(collector, "snapshot", None)
    delegated = snapshot() if callable(snapshot) else []
    proof_snapshot = getattr(collector, "proof_snapshot", None)
    tool_proofs = proof_snapshot() if callable(proof_snapshot) else []
    # Preserve explicit envelopes at every transport boundary.  Later stages
    # are authoritative for the same artifact code, but cannot erase artifacts
    # merely by replacing an envelope with prose.
    model_artifacts = merge_artifacts(
        delegated,
        extract_model_artifacts(raw_final_output),
        extract_model_artifacts(guarded_final_output),
    )
    return merge_authoritative_artifacts(model_artifacts, tool_proofs)


def _safe_identifier(value: str) -> str:
    normalized = "".join(character.lower() if character.isalnum() else "_" for character in value)
    return normalized.strip("_") or "agent"


def _sdk_version() -> str:
    try:
        return importlib.metadata.version("openai-agents")
    except importlib.metadata.PackageNotFoundError:
        return "unknown"
