from __future__ import annotations

import hashlib
import inspect
import json
from dataclasses import asdict, is_dataclass
from typing import Any, Iterable


DATA_PREVIEW_TOOL_CODE = "data_preview_query_tool"
RENDER_VALIDATE_TOOL_CODE = "render_json_validate_tool"
FINAL_ANSWER_ARTIFACT_CODE = "final-answer"
DATA_PREVIEW_ARTIFACT_CODE = "data-preview"
RENDER_DOCUMENT_ARTIFACT_CODE = "render-document"
VALIDATION_REPORT_ARTIFACT_CODE = "validation-report"
TOOL_PROOF_ARTIFACT_CODES = frozenset({
    DATA_PREVIEW_ARTIFACT_CODE,
    VALIDATION_REPORT_ARTIFACT_CODE,
})
TOOL_DERIVED_ARTIFACT_CODES = frozenset({
    *TOOL_PROOF_ARTIFACT_CODES,
    RENDER_DOCUMENT_ARTIFACT_CODE,
})
RUNTIME_OWNED_ARTIFACT_CODES = frozenset({
    FINAL_ANSWER_ARTIFACT_CODE,
    *TOOL_PROOF_ARTIFACT_CODES,
})
PROOF_BOUND_ARTIFACT_CODES = frozenset({
    DATA_PREVIEW_ARTIFACT_CODE,
    RENDER_DOCUMENT_ARTIFACT_CODE,
    VALIDATION_REPORT_ARTIFACT_CODE,
})
LIST_RENDERER_COMPONENT_CODES = frozenset({
    "zg-list-main-layout",
    "list-main-layout",
    "zg-common-list",
    "zg-common-tree-list",
    "common-list",
    "common-tree-list",
})
CONTENT_FORMAT_VALUES = frozenset({
    "PLAIN_TEXT",
    "MARKDOWN",
    "SQL",
    "JSON",
    "TABLE",
    "CARD",
})
CONTENT_FORMAT_MEDIA_TYPE_ALIASES = {
    "application/json": "JSON",
    "text/json": "JSON",
    "text/markdown": "MARKDOWN",
    "text/plain": "PLAIN_TEXT",
}


class RunArtifactCollector:
    """Collect artifacts produced anywhere in one SDK graph execution.

    Agent-as-tool delegation returns text to the parent Agent.  The parent may
    summarize that text instead of repeating its artifact envelope, so artifact
    transport cannot depend on the parent's final prose.  A collector belongs
    to one ``AgentFactory``/run and preserves the latest value for each artifact
    code without changing the delegated tool result.
    """

    def __init__(self) -> None:
        self._model_artifacts: dict[str, dict[str, Any]] = {}
        self._tool_proofs: dict[str, dict[str, Any]] = {}
        self._tool_codes: dict[str, str] = {}

    def collect_output(self, output: Any) -> list[dict[str, Any]]:
        artifacts = extract_model_artifacts(output)
        self.collect(artifacts)
        return artifacts

    def collect(self, artifacts: Iterable[dict[str, Any]]) -> None:
        for artifact in artifacts:
            code = _first_text(artifact.get("artifactCode"))
            if not code or code in RUNTIME_OWNED_ARTIFACT_CODES:
                continue
            self._model_artifacts[code] = dict(artifact)

    def snapshot(self) -> list[dict[str, Any]]:
        return [dict(artifact) for artifact in self._model_artifacts.values()]

    def proof_snapshot(self) -> list[dict[str, Any]]:
        return [dict(artifact) for artifact in self._tool_proofs.values()]

    def observe(self, event_type: str, ext: dict[str, Any], item: Any) -> None:
        """Capture proof only from the mapped lifecycle of a real tool call."""

        call_id = _first_text(ext.get("callId"))
        tool_code = _first_text(ext.get("toolCode"))
        if event_type == "tool.started":
            if call_id and tool_code:
                self._tool_codes[call_id] = tool_code
            artifact_codes = _artifact_codes_for_tool(tool_code)
            if artifact_codes:
                # A newer attempt supersedes the old proof immediately. If it
                # never completes successfully, the run must fail closed.
                for artifact_code in artifact_codes:
                    self._tool_proofs.pop(artifact_code, None)
            return
        if event_type not in {"tool.completed", "tool.failed"}:
            return
        resolved_tool_code = tool_code or (self._tool_codes.get(call_id) if call_id else None)
        if call_id:
            self._tool_codes.pop(call_id, None)
        artifact_codes = _artifact_codes_for_tool(resolved_tool_code)
        if not artifact_codes:
            return
        proofs = _tool_proofs(resolved_tool_code, item) if event_type == "tool.completed" else ()
        if proofs:
            for proof in proofs:
                self._tool_proofs[proof["artifactCode"]] = proof
        else:
            for artifact_code in artifact_codes:
                self._tool_proofs.pop(artifact_code, None)


def combine_event_observers(*observers: Any) -> Any:
    active = tuple(observer for observer in observers if callable(observer))

    def observe(event_type: str, ext: dict[str, Any], item: Any) -> None:
        for observer in active:
            try:
                observer(event_type, ext, item)
            except Exception:
                # Each observer is an optional guardrail input. One collector
                # must not prevent the other collectors from seeing the event.
                continue

    return observe


def merge_artifacts(*groups: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    """Merge artifact groups by code, with later groups taking precedence."""

    merged: dict[str, dict[str, Any]] = {}
    for group in groups:
        for artifact in group:
            code = _first_text(artifact.get("artifactCode"))
            if not code:
                continue
            merged[code] = dict(artifact)
    return list(merged.values())


def merge_authoritative_artifacts(
    model_artifacts: Iterable[dict[str, Any]],
    tool_proofs: Iterable[dict[str, Any]],
) -> list[dict[str, Any]]:
    """Merge runtime-owned proofs and bind them to the final RenderDocument.

    A RenderDocument is deliverable only when a real validator checked that
    exact document and every datasource can be traced to the successful data
    preview from this run.  Ambiguous or incomplete lineage fails closed.
    """

    trusted_model_artifacts = (
        artifact
        for artifact in model_artifacts
        if artifact.get("artifactCode") not in RUNTIME_OWNED_ARTIFACT_CODES
    )
    authoritative_tool_proofs = (
        artifact
        for artifact in tool_proofs
        if artifact.get("artifactCode") in TOOL_DERIVED_ARTIFACT_CODES
    )
    merged = merge_artifacts(trusted_model_artifacts, authoritative_tool_proofs)
    by_code = {
        artifact.get("artifactCode"): artifact
        for artifact in merged
        if isinstance(artifact, dict)
    }
    render_document = by_code.get(RENDER_DOCUMENT_ARTIFACT_CODE)
    validation = by_code.get(VALIDATION_REPORT_ARTIFACT_CODE)
    render_content = (
        render_document.get("content") if isinstance(render_document, dict) else None
    )
    validation_content = (
        validation.get("content") if isinstance(validation, dict) else None
    )
    expected_hash = render_document_hash(render_content)
    actual_hash = (
        validation_content.get("documentHash")
        if isinstance(validation_content, dict)
        else None
    )
    if (
        expected_hash is None
        or not isinstance(validation_content, dict)
        or validation_content.get("tool") != RENDER_VALIDATE_TOOL_CODE
        or validation_content.get("valid") is not True
        or actual_hash != expected_hash
    ):
        return _without_artifacts(
            merged,
            {RENDER_DOCUMENT_ARTIFACT_CODE, VALIDATION_REPORT_ARTIFACT_CODE},
        )

    data_preview = by_code.get(DATA_PREVIEW_ARTIFACT_CODE)
    preview_content = (
        data_preview.get("content") if isinstance(data_preview, dict) else None
    )
    if not _preview_matches_render(preview_content, render_content):
        return _without_artifacts(merged, PROOF_BOUND_ARTIFACT_CODES)
    return merged


def render_document_hash(value: Any) -> str | None:
    """Hash a RenderDocument with the validator's canonical JSON algorithm."""

    if not isinstance(value, dict):
        return None
    try:
        canonical = json.dumps(
            value,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
            allow_nan=False,
        )
    except (TypeError, ValueError):
        return None
    return "sha256:" + hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def extract_artifacts(final_output: Any) -> list[dict[str, Any]]:
    decoded = decode_json_value(final_output)
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
        content_format = _normalize_content_format(
            _first_text(item.get("contentFormat"), item.get("format")),
            content,
        )
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


def extract_model_artifacts(final_output: Any) -> list[dict[str, Any]]:
    return [
        artifact
        for artifact in extract_artifacts(final_output)
        if artifact.get("artifactCode") not in RUNTIME_OWNED_ARTIFACT_CODES
    ]


def _preview_matches_render(preview: Any, render_document: Any) -> bool:
    if not isinstance(preview, dict) or not isinstance(render_document, dict):
        return False
    if preview.get("tool") != DATA_PREVIEW_TOOL_CODE or preview.get("success") is not True:
        return False

    preview_model = _strict_text(preview.get("model"))
    preview_query_type = _preview_query_type(preview.get("queryType"))
    preview_fields = _preview_fields(preview)
    datasources = _render_datasources(render_document)
    if (
        preview_model is None
        or preview_query_type is None
        or not preview_fields
        or not datasources
    ):
        return False

    for datasource, renderer_fields in datasources:
        if _strict_text(datasource.get("model")) != preview_model:
            return False
        if _datasource_query_type(datasource) != preview_query_type:
            return False
        datasource_fields = _datasource_fields(datasource, renderer_fields)
        if not datasource_fields or not datasource_fields.issubset(preview_fields):
            return False
    return True


def _render_datasources(
    render_document: dict[str, Any],
) -> list[tuple[dict[str, Any], Any]] | None:
    root = render_document.get("root")
    if not isinstance(root, dict):
        return None

    datasources: list[tuple[dict[str, Any], Any]] = []
    pending = [root]
    while pending:
        node = pending.pop()
        resolved = _effective_node_datasource(node)
        if resolved is False:
            return None
        if resolved is not None:
            datasources.append(resolved)
        children = node.get("children")
        if children is None:
            continue
        if not isinstance(children, list) or any(not isinstance(child, dict) for child in children):
            return None
        pending.extend(children)
    return datasources


def _effective_node_datasource(
    node: dict[str, Any],
) -> tuple[dict[str, Any], Any] | None | bool:
    """Mirror RenderJsonRuntimeNode's schema and datasource precedence."""

    raw_props = node.get("props")
    if raw_props is None:
        props: dict[str, Any] = {}
    elif isinstance(raw_props, dict):
        props = raw_props
    else:
        return False

    schema: dict[str, Any] | None = None
    nested_schema = props.get("schema")
    if isinstance(nested_schema, dict):
        schema = nested_schema
    elif nested_schema is not None:
        return False
    elif _strict_text(node.get("component")) in LIST_RENDERER_COMPONENT_CODES:
        # List renderer aliases treat raw node props as the schema when there
        # is no nested props.schema object.
        schema = props

    renderer_fields: Any = []
    schema_datasource: Any = None
    if schema is not None:
        renderer_fields = schema.get("fields", [])
        schema_datasource = schema.get("datasource")
        if schema_datasource is not None and not isinstance(schema_datasource, dict):
            return False

    # mergeDatasource keeps schema.datasource when present and uses the node
    # datasource only as a fallback. Never validate the fallback as a cover
    # for a different datasource that the browser will actually execute.
    datasource = schema_datasource if isinstance(schema_datasource, dict) else node.get("datasource")
    if datasource is None:
        return None
    if not isinstance(datasource, dict):
        return False
    return datasource, renderer_fields


def _preview_fields(preview: dict[str, Any]) -> set[str] | None:
    columns = preview.get("columns")
    records = preview.get("records")
    if not isinstance(columns, list) or not isinstance(records, list):
        return None

    fields: set[str] = set()
    for column in columns:
        if isinstance(column, str):
            field = _strict_text(column)
            if field is None:
                return None
            fields.add(field)
            continue
        if not isinstance(column, dict):
            return None
        column_fields = {
            field
            for key in ("name", "key", "field")
            if (field := _strict_text(column.get(key))) is not None
        }
        if not column_fields:
            return None
        fields.update(column_fields)

    for record in records:
        if not isinstance(record, dict):
            return None
        for key in record:
            field = _strict_text(key)
            if field is None:
                return None
            fields.add(field)
    return fields or None


def _datasource_fields(
    datasource: dict[str, Any],
    renderer_fields: Any = None,
) -> set[str] | None:
    fields: set[str] = set()
    projected_fields: set[str] = set()
    if "fields" in datasource and not _add_text_list(
        projected_fields,
        datasource.get("fields"),
    ):
        return None
    fields.update(projected_fields)
    if "dimensions" in datasource and not _add_text_list(fields, datasource.get("dimensions")):
        return None
    for key in ("measures", "filters", "sorts"):
        if key in datasource and not _add_object_fields(fields, datasource.get(key)):
            return None

    time_range = datasource.get("timeRange")
    if time_range is not None:
        if not isinstance(time_range, dict):
            return None
        field = _strict_text(time_range.get("field"))
        if field is None:
            return None
        fields.add(field)

    filter_dict = datasource.get("filter_dict")
    if filter_dict is not None:
        if not isinstance(filter_dict, dict):
            return None
        for key in filter_dict:
            field = _strict_text(key)
            if field is None:
                return None
            fields.add(field)

    ext = datasource.get("ext")
    if ext is not None:
        if not isinstance(ext, dict):
            return None
        if "fields" in ext:
            ext_fields: set[str] = set()
            if not _add_text_list(ext_fields, ext.get("fields")):
                return None
            projected_fields.update(ext_fields)
            fields.update(ext_fields)
        if "sorts" in ext and not _add_object_fields(fields, ext.get("sorts")):
            return None
        # Explicit relation declarations can query a second model and cannot
        # be proven by the single-model preview artifact used by this flow.
        if ext.get("relations"):
            return None

    resolved_renderer_fields = _renderer_request_fields(renderer_fields)
    if resolved_renderer_fields is None:
        return None
    projected_fields.update(resolved_renderer_fields)
    fields.update(resolved_renderer_fields)
    return fields if projected_fields else None


def _renderer_request_fields(value: Any) -> set[str] | None:
    """Resolve schema.fields exactly like db-query-list-resolver.ts."""

    if value is None or not isinstance(value, list):
        return None
    fields: set[str] = set()
    for renderer_field in value:
        if not isinstance(renderer_field, dict):
            return None
        path = renderer_field.get("field")
        segments: list[str] = []
        if path is not None:
            if not isinstance(path, list):
                return None
            for segment in path:
                if not isinstance(segment, str):
                    return None
                normalized = segment.strip()
                if normalized:
                    segments.append(normalized)
        key = renderer_field.get("key")
        normalized_key = key.strip() if isinstance(key, str) else ""
        resolved = ".".join(segments) if segments else normalized_key
        if resolved:
            fields.add(resolved)
    return fields


def _add_text_list(fields: set[str], value: Any) -> bool:
    if not isinstance(value, list):
        return False
    for item in value:
        field = _strict_text(item)
        if field is None:
            return False
        fields.add(field)
    return True


def _add_object_fields(fields: set[str], value: Any) -> bool:
    if not isinstance(value, list):
        return False
    for item in value:
        if not isinstance(item, dict):
            return False
        field = _strict_text(item.get("field"))
        if field is None:
            return False
        fields.add(field)
    return True


def _preview_query_type(value: Any) -> str | None:
    query_type = _strict_text(value)
    return query_type if query_type in {"LIST", "AGGREGATE"} else None


def _datasource_query_type(datasource: dict[str, Any]) -> str | None:
    datasource_type = _strict_text(datasource.get("type"))
    if datasource_type == "db-query-list":
        return "LIST" if datasource.get("queryType") is None else None
    if datasource_type != "semantic-query":
        return None
    query_type = _strict_text(datasource.get("queryType"))
    if query_type == "list":
        return "LIST"
    if query_type in {"count", "aggregate"}:
        return "AGGREGATE"
    return None


def _strict_text(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    text = value.strip()
    return text if text and text == value else None


def _without_artifacts(
    artifacts: Iterable[dict[str, Any]],
    codes: Iterable[str],
) -> list[dict[str, Any]]:
    rejected = set(codes)
    return [
        artifact
        for artifact in artifacts
        if artifact.get("artifactCode") not in rejected
    ]


def decode_json_value(value: Any) -> Any:
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


def _tool_proofs(tool_code: str | None, item: Any) -> tuple[dict[str, Any], ...]:
    output = _tool_output(item)
    if output is None or output.get("tool") != tool_code:
        return ()
    if tool_code == DATA_PREVIEW_TOOL_CODE and output.get("success") is True:
        return (_proof_artifact(DATA_PREVIEW_ARTIFACT_CODE, output),)
    if tool_code == RENDER_VALIDATE_TOOL_CODE and output.get("valid") is True:
        document = output.get("renderDocument")
        if not isinstance(document, dict):
            return ()
        if output.get("documentHash") != render_document_hash(document):
            return ()
        report = dict(output)
        report.pop("renderDocument", None)
        return (
            {
                "artifactCode": RENDER_DOCUMENT_ARTIFACT_CODE,
                "artifactType": "RENDER_JSON",
                "contentFormat": "JSON",
                "content": document,
            },
            _proof_artifact(VALIDATION_REPORT_ARTIFACT_CODE, report),
        )
    return ()


def _artifact_codes_for_tool(tool_code: str | None) -> frozenset[str]:
    if tool_code == DATA_PREVIEW_TOOL_CODE:
        return frozenset({DATA_PREVIEW_ARTIFACT_CODE})
    if tool_code == RENDER_VALIDATE_TOOL_CODE:
        return frozenset({RENDER_DOCUMENT_ARTIFACT_CODE, VALIDATION_REPORT_ARTIFACT_CODE})
    return frozenset()


def _tool_output(item: Any) -> dict[str, Any] | None:
    raw_item = _attribute(item, "raw_item")
    for owner in (item, raw_item):
        for attribute in ("output", "result"):
            decoded = decode_json_value(_attribute(owner, attribute))
            if isinstance(decoded, dict):
                return decoded
    return None


def _proof_artifact(code: str, content: dict[str, Any]) -> dict[str, Any]:
    return {
        "artifactCode": code,
        "artifactType": "JSON",
        "contentFormat": "JSON",
        "content": dict(content),
    }


def _attribute(value: Any, name: str) -> Any:
    if isinstance(value, dict):
        return value.get(name)
    return getattr(value, name, None)


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


def _normalize_content_format(value: str | None, content: Any) -> str:
    """Normalize wire media types into the platform content-format enum."""

    if not value:
        return "JSON" if isinstance(content, (dict, list)) else "PLAIN_TEXT"
    media_type = value.split(";", 1)[0].strip().lower()
    aliased = CONTENT_FORMAT_MEDIA_TYPE_ALIASES.get(media_type)
    if aliased is not None:
        return aliased
    normalized = value.strip().upper()
    return normalized if normalized in CONTENT_FORMAT_VALUES else value
