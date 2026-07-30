from __future__ import annotations

import json
import re
from collections import Counter
from dataclasses import dataclass, field
from typing import Any

from ..artifacts import render_document_hash


RULE_VERSION = "render-validator/1.0.0"
SUPPORTED_PROTOCOL_VERSIONS = {"1.0", "1.0.0"}
MAX_DOCUMENT_BYTES = 1024 * 1024
MAX_NODES = 1000
MAX_DEPTH = 32
COMPONENT_SKILL_REF = "skill://render-json-authoring/v6"
ALLOWED_NODE_KEYS = {
    "id", "component", "componentVersion", "props", "layout", "datasource",
    "bindings", "events", "actions", "children",
}
ALLOWED_DOCUMENT_KEYS = {"protocol", "protocolVersion", "pageId", "revision", "root"}
ALLOWED_DATASOURCE_TYPES = {
    # Current frontend resolver types.
    "direct-json",
    "db-query-list",
    # Phase-1 proof-bound variants used by the application-build contract.
    "semantic-query",
    "preview-result",
    "static",
}
ALLOWED_DATASOURCE_KEYS = {
    "key", "type", "model", "page", "page_size", "filter_dict", "filterExpr", "ext",
    "fields", "queryType", "dimensions", "measures", "filters", "timeRange", "sorts", "limit",
    "contractRef", "previewProofRef", "summary", "data",
}
ALLOWED_BINDING_KEYS = {"source", "transform", "fallback"}
ALLOWED_EVENT_KEYS = {"event", "name", "type", "actionRef", "action"}
ALLOWED_ACTION_KEYS = {"key", "type", "action", "target", "params"}
ALLOWED_TRANSFORMS = {
    "identity", "number", "percent", "currency", "date", "datetime", "category-series",
}
ALLOWED_QUERY_TYPES = {"list", "count", "aggregate"}
ALLOWED_AGGREGATIONS = {"count", "sum", "min", "max", "avg"}
ALLOWED_QUERY_FILTER_OPERATORS = {
    "eq", "ne", "gt", "gte", "lt", "lte", "in", "not_in", "is_null", "is_not_null",
    "like", "starts_with", "ends_with",
}
_STABLE_KEY_PATTERN = re.compile(r"^[A-Za-z][A-Za-z0-9_.:-]{0,127}$")
_SEMANTIC_PATH_PATTERN = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)*(?:\[\d+\])?$")
_FILTER_EXPR_TOKEN_PATTERN = re.compile(r"\s*(?:[A-Za-z][A-Za-z0-9_.]*|\(|\)|$)")
ALLOWED_ACTION_TYPES = {
    "filter.update",
    "selection.update",
    "navigation.local",
    "refresh",
    "dialog.open",
}
FORBIDDEN_NORMALIZED_KEYS = {
    "sql", "rawsql", "statement", "querytext", "url", "uri", "endpoint", "requesturl",
    "baseurl", "headers", "authorization", "credential", "credentials", "password", "secret",
    "token", "apikey", "cookie", "fetch", "httpclient", "proto", "prototype", "constructor",
    "connectionstring", "privatekey", "clientsecret", "certificate", "command", "script",
    "expression", "handler", "resolver", "module", "packageuri", "socket", "proxy",
}
_DANGEROUS_STRING_PATTERNS = (
    re.compile(r"javascript\s*:", re.IGNORECASE),
    re.compile(r"data\s*:\s*(?:text/html|application/javascript)", re.IGNORECASE),
    re.compile(r"(?:^|[=\s])function\s*\(", re.IGNORECASE),
    re.compile(r"\bnew\s+Function\s*\(", re.IGNORECASE),
    re.compile(r"\beval\s*\(", re.IGNORECASE),
    re.compile(r"=>"),
    re.compile(r"^\s*=\s*(?:async\s+)?function\b", re.IGNORECASE),
    re.compile(r"\b[A-Za-z][A-Za-z0-9+.-]*:\/\/", re.IGNORECASE),
    re.compile(r"\b(?:javascript|data|blob|vbscript):", re.IGNORECASE),
    re.compile(r"(?i)\b(?:select|insert|update|delete|drop|alter|create)\b[\s\S]{0,160}\b(?:from|into|set|table)\b"),
    re.compile(r"\$\{[^}]+\}"),
)


@dataclass
class ValidationState:
    errors: list[dict[str, Any]] = field(default_factory=list)
    warnings: list[dict[str, Any]] = field(default_factory=list)
    node_ids: set[str] = field(default_factory=set)
    component_keys: set[str] = field(default_factory=set)
    node_count: int = 0
    max_depth: int = 0

    def error(
        self,
        code: str,
        message: str,
        json_path: str,
        node_id: str | None = None,
        recoverable: bool = True,
    ) -> None:
        self.errors.append(_issue(code, message, json_path, node_id, recoverable, "ERROR"))

    def warning(
        self,
        code: str,
        message: str,
        json_path: str,
        node_id: str | None = None,
        recoverable: bool = True,
    ) -> None:
        self.warnings.append(_issue(code, message, json_path, node_id, recoverable, "WARNING"))


def validate_render_document(
    render_json: str | dict[str, Any],
) -> dict[str, Any]:
    parsed, parse_issue = _parse_document(render_json)
    if parse_issue is not None:
        return _report(None, ValidationState(errors=[parse_issue]))

    state = ValidationState()
    if not isinstance(parsed, dict):
        state.error("SCHEMA_INVALID", "Render document root must be an object", "$", recoverable=False)
        return _report(parsed, state)

    _validate_document_shape(parsed, state)
    root = parsed.get("root")
    if isinstance(root, dict):
        _walk_node(root, "$.root", 1, state)

    return _report(parsed, state)


def _parse_document(value: str | dict[str, Any]) -> tuple[Any | None, dict[str, Any] | None]:
    if isinstance(value, dict):
        try:
            encoded = json.dumps(
                value,
                ensure_ascii=False,
                separators=(",", ":"),
                allow_nan=False,
            ).encode("utf-8")
        except (TypeError, ValueError, RecursionError) as exc:
            return None, _issue(
                "JSON_PARSE_FAILED",
                f"Render document cannot be serialized as JSON: {exc}",
                "$",
                None,
                True,
            )
        if len(encoded) > MAX_DOCUMENT_BYTES:
            return None, _issue(
                "DOCUMENT_TOO_LARGE",
                f"Render document exceeds {MAX_DOCUMENT_BYTES} bytes",
                "$",
                None,
                False,
            )
        return value, None
    if not isinstance(value, str) or not value.strip():
        return None, _issue(
            "JSON_PARSE_FAILED", "render_json is required", "$", None, True,
        )
    if len(value.encode("utf-8")) > MAX_DOCUMENT_BYTES:
        return None, _issue(
            "DOCUMENT_TOO_LARGE",
            f"Render document exceeds {MAX_DOCUMENT_BYTES} bytes",
            "$",
            None,
            False,
        )
    try:
        return json.loads(
            value,
            parse_constant=_reject_json_constant,
            object_pairs_hook=_unique_json_object,
        ), None
    except json.JSONDecodeError as exc:
        return None, _issue(
            "JSON_PARSE_FAILED",
            f"Invalid JSON at line {exc.lineno}, column {exc.colno}: {exc.msg}",
            "$",
            None,
            True,
        )
    except ValueError as exc:
        return None, _issue(
            "JSON_PARSE_FAILED",
            f"Invalid JSON: {exc}",
            "$",
            None,
            True,
        )


def _reject_json_constant(value: str) -> None:
    raise ValueError(f"non-standard numeric constant is forbidden: {value}")


def _unique_json_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate object key is forbidden: {key}")
        result[key] = value
    return result


def _validate_document_shape(document: dict[str, Any], state: ValidationState) -> None:
    for key in sorted(set(document) - ALLOWED_DOCUMENT_KEYS):
        state.error("SCHEMA_INVALID", f"Unknown Render document field: {key}", f"$.{key}")
    protocol = document.get("protocol")
    if protocol != "render-json":
        state.error("SCHEMA_INVALID", "protocol must equal 'render-json'", "$.protocol")
    protocol_version = document.get("protocolVersion")
    if not isinstance(protocol_version, str) or not protocol_version.strip():
        state.error("SCHEMA_INVALID", "protocolVersion is required", "$.protocolVersion")
    elif protocol_version.strip() not in SUPPORTED_PROTOCOL_VERSIONS:
        state.error(
            "PROTOCOL_VERSION_UNSUPPORTED",
            f"Unsupported protocolVersion: {protocol_version}",
            "$.protocolVersion",
            recoverable=False,
        )
    page_id = document.get("pageId")
    if not isinstance(page_id, str) or not page_id.strip():
        state.error("SCHEMA_INVALID", "pageId is required", "$.pageId")
    elif not re.fullmatch(r"[A-Za-z][A-Za-z0-9_.:-]{0,127}", page_id.strip()):
        state.error("SCHEMA_INVALID", "pageId must be a stable identifier", "$.pageId")
    revision = document.get("revision")
    if revision is not None and (not isinstance(revision, str) or not revision.strip()):
        state.error("SCHEMA_INVALID", "revision must be non-empty text", "$.revision")
    if not isinstance(document.get("root"), dict):
        state.error("SCHEMA_INVALID", "root must be a Render node object", "$.root")
    for key, value in document.items():
        if key != "root":
            _scan_security(value, f"$.{key}", None, state)


def _walk_node(node: dict[str, Any], path: str, depth: int, state: ValidationState) -> None:
    state.node_count += 1
    state.max_depth = max(state.max_depth, depth)
    if state.node_count > MAX_NODES:
        if not any(issue["code"] == "NODE_LIMIT_EXCEEDED" for issue in state.errors):
            state.error(
                "NODE_LIMIT_EXCEEDED",
                f"Render document exceeds {MAX_NODES} nodes",
                path,
                recoverable=False,
            )
        return
    if depth > MAX_DEPTH:
        state.error(
            "NODE_DEPTH_EXCEEDED",
            f"Render node depth exceeds {MAX_DEPTH}",
            path,
            recoverable=False,
        )
        return

    node_id_value = node.get("id")
    node_id = node_id_value.strip() if isinstance(node_id_value, str) and node_id_value.strip() else None
    if node_id is None:
        state.error("SCHEMA_INVALID", "node.id is required", f"{path}.id")
    elif node_id in state.node_ids:
        state.error("DUPLICATE_NODE_ID", f"Duplicate node id: {node_id}", f"{path}.id", node_id)
    else:
        state.node_ids.add(node_id)

    # Scan this node with its stable identity. Children are scanned when they
    # are visited, preventing duplicate security issues with the same nodeId.
    _scan_security(
        {key: value for key, value in node.items() if key != "children"},
        path,
        node_id,
        state,
    )

    component_value = node.get("component")
    component = component_value.strip() if isinstance(component_value, str) and component_value.strip() else None
    if component is None:
        state.error("SCHEMA_INVALID", "node.component is required", f"{path}.component", node_id)
    elif not re.fullmatch(r"[A-Za-z][A-Za-z0-9_.:-]{0,127}", component):
        state.error("SCHEMA_INVALID", f"Invalid component key: {component}", f"{path}.component", node_id)
    else:
        state.component_keys.add(component)

    raw_component_version = node.get("componentVersion")
    component_version = _text(raw_component_version)
    if raw_component_version is not None and component_version is None:
        state.error(
            "SCHEMA_INVALID",
            "node.componentVersion must be non-empty text",
            f"{path}.componentVersion",
            node_id,
        )
    elif component_version is None:
        state.error(
            "COMPONENT_VERSION_REQUIRED",
            "node.componentVersion must pin the version documented by render-json-authoring skill",
            f"{path}.componentVersion",
            node_id,
        )
    elif not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+-]{0,127}", component_version):
        state.error(
            "COMPONENT_VERSION_INVALID",
            f"Invalid component version: {component_version}",
            f"{path}.componentVersion",
            node_id,
        )

    unknown_keys = sorted(set(node) - ALLOWED_NODE_KEYS)
    for key in unknown_keys:
        state.error("SCHEMA_INVALID", f"Unknown Render node field: {key}", f"{path}.{key}", node_id)

    for key in ("props", "layout", "datasource", "bindings"):
        value = node.get(key)
        if value is not None and not isinstance(value, dict):
            state.error("SCHEMA_INVALID", f"{key} must be an object", f"{path}.{key}", node_id)
    for key in ("events", "actions", "children"):
        value = node.get(key)
        if value is not None and not isinstance(value, list):
            state.error("SCHEMA_INVALID", f"{key} must be an array", f"{path}.{key}", node_id)

    datasource = node.get("datasource")
    if isinstance(datasource, dict):
        _validate_datasource(datasource, f"{path}.datasource", node_id, state)
    bindings = node.get("bindings")
    if isinstance(bindings, dict):
        _validate_bindings(bindings, f"{path}.bindings", node_id, state)
    actions = node.get("actions")
    action_keys = _validate_actions(actions, f"{path}.actions", node_id, state)
    _validate_events(node.get("events"), f"{path}.events", node_id, action_keys, state)

    children = node.get("children")
    if isinstance(children, list):
        for index, child in enumerate(children):
            child_path = f"{path}.children[{index}]"
            if not isinstance(child, dict):
                state.error("SCHEMA_INVALID", "child must be a Render node object", child_path, node_id)
                continue
            _walk_node(child, child_path, depth + 1, state)


def _validate_datasource(
    datasource: dict[str, Any],
    path: str,
    node_id: str | None,
    state: ValidationState,
) -> None:
    for key in sorted(set(datasource) - ALLOWED_DATASOURCE_KEYS):
        state.error("SCHEMA_INVALID", f"Unknown datasource field: {key}", f"{path}.{key}", node_id)
    datasource_type = datasource.get("type")
    if not isinstance(datasource_type, str) or datasource_type not in ALLOWED_DATASOURCE_TYPES:
        state.error(
            "DATA_RESOLVER_NOT_FOUND",
            f"Unknown or missing datasource.type: {datasource_type}",
            f"{path}.type",
            node_id,
        )
        return
    key = datasource.get("key")
    if not isinstance(key, str) or not _STABLE_KEY_PATTERN.fullmatch(key.strip()):
        state.error("SCHEMA_INVALID", "datasource.key is required", f"{path}.key", node_id)
    query_type = datasource.get("queryType")
    if query_type is not None:
        if not isinstance(query_type, str) or query_type not in ALLOWED_QUERY_TYPES:
            state.error(
                "SCHEMA_INVALID",
                f"Unsupported datasource.queryType: {query_type}",
                f"{path}.queryType",
                node_id,
            )
        elif datasource_type != "semantic-query":
            state.error(
                "SCHEMA_INVALID",
                "datasource.queryType is only supported by semantic-query",
                f"{path}.queryType",
                node_id,
            )
        else:
            _validate_semantic_query_shape(datasource, query_type, path, node_id, state)
    if datasource_type in {"db-query-list", "semantic-query"}:
        model = datasource.get("model")
        if not isinstance(model, str) or not re.fullmatch(r"[A-Za-z][A-Za-z0-9_.-]{0,127}", model.strip()):
            state.error("SCHEMA_INVALID", "datasource.model must be a stable semantic identifier", f"{path}.model", node_id)
    if datasource_type in {"db-query-list", "semantic-query"}:
        filter_dict = datasource.get("filter_dict")
        if filter_dict is not None:
            _validate_filter_dict(filter_dict, f"{path}.filter_dict", node_id, state)
        filter_expr = datasource.get("filterExpr")
        if filter_expr is not None:
            _validate_filter_expr(filter_expr, filter_dict, f"{path}.filterExpr", node_id, state)
        page_size = datasource.get("page_size")
        if page_size is not None and (
            isinstance(page_size, bool) or not isinstance(page_size, int) or not 1 <= page_size <= 100
        ):
            state.error("SCHEMA_INVALID", "datasource.page_size must be between 1 and 100", f"{path}.page_size", node_id)
    if datasource_type == "semantic-query":
        for key_name in ("contractRef", "previewProofRef"):
            value = datasource.get(key_name)
            if not isinstance(value, str) or not value.strip():
                state.error("SCHEMA_INVALID", f"datasource.{key_name} is required", f"{path}.{key_name}", node_id)
    if datasource_type in {"preview-result", "static"}:
        if datasource_type == "preview-result":
            proof = datasource.get("previewProofRef")
            if not isinstance(proof, str) or not proof.strip():
                state.error("SCHEMA_INVALID", "preview-result requires previewProofRef", f"{path}.previewProofRef", node_id)
        elif "data" not in datasource:
            state.error("SCHEMA_INVALID", "static datasource requires data", f"{path}.data", node_id)
    fields = datasource.get("fields")
    if fields is not None:
        if not isinstance(fields, list) or len(fields) > 100:
            state.error("SCHEMA_INVALID", "datasource.fields must be an array of at most 100 items", f"{path}.fields", node_id)
        else:
            seen_fields: set[str] = set()
            for index, field in enumerate(fields):
                if not isinstance(field, str) or not re.fullmatch(r"[A-Za-z][A-Za-z0-9_.-]{0,127}", field.strip()):
                    state.error("SCHEMA_INVALID", "datasource field must be a stable semantic identifier", f"{path}.fields[{index}]", node_id)
                elif field in seen_fields:
                    state.error("SCHEMA_INVALID", "datasource.fields must not contain duplicates", f"{path}.fields[{index}]", node_id)
                else:
                    seen_fields.add(field)
    for text_key in ("contractRef", "previewProofRef"):
        if text_key in datasource and not isinstance(datasource[text_key], str):
            state.error("SCHEMA_INVALID", f"datasource.{text_key} must be text", f"{path}.{text_key}", node_id)
    if "page" in datasource and (
        isinstance(datasource["page"], bool)
        or not isinstance(datasource["page"], int)
        or datasource["page"] < 1
    ):
        state.error("SCHEMA_INVALID", "datasource.page must be a positive integer", f"{path}.page", node_id)
    if "filter_dict" in datasource and not isinstance(datasource.get("filter_dict"), dict):
        state.error("SCHEMA_INVALID", "datasource.filter_dict must be an object", f"{path}.filter_dict", node_id)
    if "filterExpr" in datasource and not isinstance(datasource.get("filterExpr"), str):
        state.error("SCHEMA_INVALID", "datasource.filterExpr must be text", f"{path}.filterExpr", node_id)


def _validate_semantic_query_shape(
    datasource: dict[str, Any],
    query_type: str,
    path: str,
    node_id: str | None,
    state: ValidationState,
) -> None:
    """Validate the proof-bound query declaration used by count and aggregates."""

    fields = datasource.get("fields")
    if not isinstance(fields, list) or not fields:
        state.error("SCHEMA_INVALID", "semantic query requires at least one concrete field", f"{path}.fields", node_id)
    dimensions = _validate_query_dimensions(datasource.get("dimensions"), f"{path}.dimensions", node_id, state)
    measures = _validate_query_measures(datasource.get("measures"), f"{path}.measures", node_id, state)
    _validate_query_filters(datasource.get("filters"), f"{path}.filters", node_id, state)
    _validate_time_range(datasource.get("timeRange"), f"{path}.timeRange", node_id, state)
    _validate_query_sorts(datasource.get("sorts"), f"{path}.sorts", node_id, state)

    limit = datasource.get("limit")
    if limit is not None and (
        isinstance(limit, bool) or not isinstance(limit, int) or not 1 <= limit <= 100
    ):
        state.error("SCHEMA_INVALID", "datasource.limit must be between 1 and 100", f"{path}.limit", node_id)
    if "filter_dict" in datasource and "filters" in datasource:
        state.error(
            "SCHEMA_INVALID",
            "semantic query must use filters instead of combining filters with filter_dict",
            path,
            node_id,
        )

    concrete_fields = {
        field.strip()
        for field in fields
        if isinstance(field, str) and re.fullmatch(r"[A-Za-z][A-Za-z0-9_.-]{0,127}", field.strip())
    } if isinstance(fields, list) else set()
    for index, dimension in enumerate(dimensions):
        if dimension not in concrete_fields:
            state.error(
                "SCHEMA_INVALID",
                "datasource.dimensions field must also be declared in datasource.fields",
                f"{path}.dimensions[{index}]",
                node_id,
            )
    for index, measure in enumerate(measures):
        field = measure.get("field")
        if isinstance(field, str) and field not in concrete_fields:
            state.error(
                "SCHEMA_INVALID",
                "datasource.measures field must also be declared in datasource.fields",
                f"{path}.measures[{index}].field",
                node_id,
            )

    if query_type == "list":
        if dimensions:
            state.error("SCHEMA_INVALID", "list query must not declare dimensions", f"{path}.dimensions", node_id)
        if measures:
            state.error("SCHEMA_INVALID", "list query must not declare measures", f"{path}.measures", node_id)
    elif query_type == "count":
        if dimensions:
            state.error("SCHEMA_INVALID", "count query must not declare dimensions", f"{path}.dimensions", node_id)
        if len(measures) != 1 or any(measure.get("aggregation") != "count" for measure in measures):
            state.error(
                "SCHEMA_INVALID",
                "count query requires exactly one measure using count aggregation",
                f"{path}.measures",
                node_id,
            )
    elif query_type == "aggregate" and not measures:
        state.error("SCHEMA_INVALID", "aggregate query requires at least one measure", f"{path}.measures", node_id)


def _validate_query_dimensions(
    value: Any,
    path: str,
    node_id: str | None,
    state: ValidationState,
) -> list[str]:
    if value is None:
        return []
    if not isinstance(value, list) or len(value) > 50:
        state.error("SCHEMA_INVALID", "datasource.dimensions must be an array of at most 50 fields", path, node_id)
        return []
    dimensions: list[str] = []
    seen: set[str] = set()
    for index, field in enumerate(value):
        if not isinstance(field, str) or not _STABLE_KEY_PATTERN.fullmatch(field.strip()):
            state.error("SCHEMA_INVALID", "dimension must be a stable semantic identifier", f"{path}[{index}]", node_id)
        elif field in seen:
            state.error("SCHEMA_INVALID", "datasource.dimensions must not contain duplicates", f"{path}[{index}]", node_id)
        else:
            seen.add(field)
            dimensions.append(field)
    return dimensions


def _validate_query_measures(
    value: Any,
    path: str,
    node_id: str | None,
    state: ValidationState,
) -> list[dict[str, Any]]:
    if value is None:
        return []
    if not isinstance(value, list) or len(value) > 50:
        state.error("SCHEMA_INVALID", "datasource.measures must be an array of at most 50 measures", path, node_id)
        return []
    measures: list[dict[str, Any]] = []
    aliases: set[str] = set()
    for index, measure in enumerate(value):
        measure_path = f"{path}[{index}]"
        if not isinstance(measure, dict):
            state.error("SCHEMA_INVALID", "measure must be an object", measure_path, node_id)
            continue
        for key in sorted(set(measure) - {"field", "aggregation", "label", "alias"}):
            state.error("SCHEMA_INVALID", f"Unknown measure field: {key}", f"{measure_path}.{key}", node_id)
        field = measure.get("field")
        if not isinstance(field, str) or not _STABLE_KEY_PATTERN.fullmatch(field.strip()):
            state.error("SCHEMA_INVALID", "measure.field must be a stable semantic identifier", f"{measure_path}.field", node_id)
        aggregation = measure.get("aggregation")
        if aggregation not in ALLOWED_AGGREGATIONS:
            state.error("SCHEMA_INVALID", f"Unsupported measure aggregation: {aggregation}", f"{measure_path}.aggregation", node_id)
        label = measure.get("label")
        if label is not None and (not isinstance(label, str) or not label.strip()):
            state.error("SCHEMA_INVALID", "measure.label must be non-empty text", f"{measure_path}.label", node_id)
        alias = measure.get("alias")
        if alias is not None and (not isinstance(alias, str) or not _STABLE_KEY_PATTERN.fullmatch(alias.strip())):
            state.error("SCHEMA_INVALID", "measure.alias must be a stable identifier", f"{measure_path}.alias", node_id)
        elif isinstance(alias, str):
            if alias in aliases:
                state.error("SCHEMA_INVALID", "datasource.measures must not reuse aliases", f"{measure_path}.alias", node_id)
            aliases.add(alias)
        measures.append(measure)
    return measures


def _validate_query_filters(
    value: Any,
    path: str,
    node_id: str | None,
    state: ValidationState,
) -> None:
    if value is None:
        return
    if not isinstance(value, list) or len(value) > 50:
        state.error("SCHEMA_INVALID", "datasource.filters must be an array of at most 50 filters", path, node_id)
        return
    for index, query_filter in enumerate(value):
        filter_path = f"{path}[{index}]"
        if not isinstance(query_filter, dict):
            state.error("SCHEMA_INVALID", "query filter must be an object", filter_path, node_id)
            continue
        for key in sorted(set(query_filter) - {"field", "operator", "value", "values"}):
            state.error("SCHEMA_INVALID", f"Unknown query filter field: {key}", f"{filter_path}.{key}", node_id)
        field = query_filter.get("field")
        if not isinstance(field, str) or not _STABLE_KEY_PATTERN.fullmatch(field.strip()):
            state.error("SCHEMA_INVALID", "query filter field must be a stable semantic identifier", f"{filter_path}.field", node_id)
        operator = query_filter.get("operator")
        if operator not in ALLOWED_QUERY_FILTER_OPERATORS:
            state.error("SCHEMA_INVALID", f"Unsupported query filter operator: {operator}", f"{filter_path}.operator", node_id)
            continue
        has_value = "value" in query_filter
        has_values = "values" in query_filter
        if operator in {"in", "not_in"}:
            if has_value or not has_values or not isinstance(query_filter.get("values"), list) or not query_filter["values"]:
                state.error("SCHEMA_INVALID", f"{operator} filter requires a non-empty values array only", filter_path, node_id)
            elif not _bounded_json_value(query_filter["values"]):
                state.error("SCHEMA_INVALID", "query filter values are too complex", f"{filter_path}.values", node_id)
        elif operator in {"is_null", "is_not_null"}:
            if has_value or has_values:
                state.error("SCHEMA_INVALID", f"{operator} filter must not declare value or values", filter_path, node_id)
        elif not has_value or has_values:
            state.error("SCHEMA_INVALID", f"{operator} filter requires value and must not declare values", filter_path, node_id)
        elif not _bounded_json_value(query_filter.get("value")):
            state.error("SCHEMA_INVALID", "query filter value is too complex", f"{filter_path}.value", node_id)


def _validate_time_range(
    value: Any,
    path: str,
    node_id: str | None,
    state: ValidationState,
) -> None:
    if value is None:
        return
    if not isinstance(value, dict):
        state.error("SCHEMA_INVALID", "datasource.timeRange must be an object", path, node_id)
        return
    for key in sorted(set(value) - {"field", "preset", "start", "end"}):
        state.error("SCHEMA_INVALID", f"Unknown timeRange field: {key}", f"{path}.{key}", node_id)
    field = value.get("field")
    if not isinstance(field, str) or not _STABLE_KEY_PATTERN.fullmatch(field.strip()):
        state.error("SCHEMA_INVALID", "timeRange.field must be a stable semantic identifier", f"{path}.field", node_id)
    preset = value.get("preset")
    start = value.get("start")
    end = value.get("end")
    has_preset = isinstance(preset, str) and bool(preset.strip())
    has_bounds = isinstance(start, str) and bool(start.strip()) and isinstance(end, str) and bool(end.strip())
    if has_preset == has_bounds:
        state.error("SCHEMA_INVALID", "timeRange requires exactly one of preset or start/end", path, node_id)
    elif (preset is not None and not has_preset) or ((start is not None or end is not None) and not has_bounds):
        state.error("SCHEMA_INVALID", "timeRange preset and bounds must be non-empty text", path, node_id)


def _validate_query_sorts(
    value: Any,
    path: str,
    node_id: str | None,
    state: ValidationState,
) -> None:
    if value is None:
        return
    if not isinstance(value, list) or len(value) > 50:
        state.error("SCHEMA_INVALID", "datasource.sorts must be an array of at most 50 sorts", path, node_id)
        return
    for index, sort in enumerate(value):
        sort_path = f"{path}[{index}]"
        if not isinstance(sort, dict):
            state.error("SCHEMA_INVALID", "sort must be an object", sort_path, node_id)
            continue
        for key in sorted(set(sort) - {"field", "direction"}):
            state.error("SCHEMA_INVALID", f"Unknown sort field: {key}", f"{sort_path}.{key}", node_id)
        field = sort.get("field")
        if not isinstance(field, str) or not _STABLE_KEY_PATTERN.fullmatch(field.strip()):
            state.error("SCHEMA_INVALID", "sort.field must be a stable semantic identifier", f"{sort_path}.field", node_id)
        direction = sort.get("direction")
        if direction is not None and direction not in {"asc", "desc", "ASC", "DESC"}:
            state.error("SCHEMA_INVALID", "sort.direction must be ASC or DESC", f"{sort_path}.direction", node_id)


def _validate_bindings(
    bindings: dict[str, Any],
    path: str,
    node_id: str | None,
    state: ValidationState,
) -> None:
    for key, value in bindings.items():
        binding_path = f"{path}.{key}"
        if not isinstance(key, str) or not key.strip():
            state.error("SCHEMA_INVALID", "binding key must be non-empty", binding_path, node_id)
        if not isinstance(value, dict):
            state.error("SCHEMA_INVALID", "binding must be an object", binding_path, node_id)
            continue
        for field in sorted(set(value) - ALLOWED_BINDING_KEYS):
            state.error("SCHEMA_INVALID", f"Unknown binding field: {field}", f"{binding_path}.{field}", node_id)
        source = value.get("source")
        if not isinstance(source, str) or not _SEMANTIC_PATH_PATTERN.fullmatch(source.strip()):
            state.error("SCHEMA_INVALID", "binding.source is required", f"{binding_path}.source", node_id)
        transform = value.get("transform")
        if transform is not None and transform not in ALLOWED_TRANSFORMS:
            state.error("SCHEMA_INVALID", f"Unsupported binding transform: {transform}", f"{binding_path}.transform", node_id)


def _validate_filter_dict(
    value: Any,
    path: str,
    node_id: str | None,
    state: ValidationState,
) -> None:
    if not isinstance(value, dict):
        return
    if len(value) > 50:
        state.error("SCHEMA_INVALID", "filter_dict may contain at most 50 conditions", path, node_id)
    for key, condition in value.items():
        condition_path = f"{path}.{key}"
        if not isinstance(key, str) or not re.fullmatch(r"[A-Za-z][A-Za-z0-9_.]{0,127}", key):
            state.error("SCHEMA_INVALID", "filter_dict key must be a stable identifier", condition_path, node_id)
        if isinstance(condition, dict):
            allowed = {"op", "value"}
            for extra in sorted(set(condition) - allowed):
                state.error("SCHEMA_INVALID", f"Unknown filter condition field: {extra}", f"{condition_path}.{extra}", node_id)
            operator = condition.get("op", "eq")
            if not isinstance(operator, str) or not re.fullmatch(r"(?:eq|ne|neq|gt|gte|ge|lt|lte|le|like|prefix_like|suffix_like|in|not_in|is_null|is_not_null)", operator, re.IGNORECASE):
                state.error("SCHEMA_INVALID", "Unsupported filter operator", f"{condition_path}.op", node_id)
            if "value" in condition and not _bounded_json_value(condition.get("value")):
                state.error("SCHEMA_INVALID", "filter value is too complex", f"{condition_path}.value", node_id)
        elif not _bounded_json_value(condition):
            state.error("SCHEMA_INVALID", "filter value is too complex", condition_path, node_id)


def _validate_filter_expr(
    value: Any,
    filter_dict: Any,
    path: str,
    node_id: str | None,
    state: ValidationState,
) -> None:
    if not isinstance(value, str) or not value.strip():
        state.error("SCHEMA_INVALID", "filterExpr must be non-empty text", path, node_id)
        return
    if len(value) > 512:
        state.error("SCHEMA_INVALID", "filterExpr is too long", path, node_id)
        return
    tokens = re.findall(r"[A-Za-z][A-Za-z0-9_.]*|[()]", value)
    remainder = re.sub(r"[A-Za-z][A-Za-z0-9_.]*|[()]|\s+", "", value)
    if remainder:
        state.error("SECURITY_VIOLATION", "filterExpr contains unsupported expression syntax", path, node_id, recoverable=False)
        return
    identifiers: list[str] = []
    cursor = 0
    parse_error = False

    def peek() -> str | None:
        return tokens[cursor] if cursor < len(tokens) else None

    def consume() -> str | None:
        nonlocal cursor
        token = peek()
        if token is not None:
            cursor += 1
        return token

    def parse_or() -> bool:
        if not parse_and():
            return False
        while (token := peek()) is not None and token.lower() == "or":
            consume()
            if not parse_and():
                return False
        return True

    def parse_and() -> bool:
        if not parse_primary():
            return False
        while (token := peek()) is not None and token.lower() == "and":
            consume()
            if not parse_primary():
                return False
        return True

    def parse_primary() -> bool:
        token = peek()
        if token == "(":
            consume()
            if not parse_or() or consume() != ")":
                return False
            return True
        if token is None or token in {")", "("} or token.lower() in {"and", "or"}:
            return False
        identifiers.append(token)
        consume()
        return True

    if not parse_or() or cursor != len(tokens):
        parse_error = True
    if parse_error:
        state.error("SCHEMA_INVALID", "filterExpr has invalid boolean expression syntax", path, node_id)
        return
    available = set(filter_dict) if isinstance(filter_dict, dict) else set()
    if not available:
        state.error("SCHEMA_INVALID", "filterExpr requires filter_dict conditions", path, node_id)
    elif Counter(identifiers) != Counter(available):
        state.error("SCHEMA_INVALID", "filterExpr must reference each filter_dict key exactly once", path, node_id)


def _validate_actions(
    actions: Any,
    path: str,
    node_id: str | None,
    state: ValidationState,
) -> set[str]:
    if actions is None:
        return set()
    if not isinstance(actions, list):
        return set()
    keys: set[str] = set()
    for index, action in enumerate(actions):
        action_path = f"{path}[{index}]"
        if not isinstance(action, dict):
            state.error("SCHEMA_INVALID", "action must be an object", action_path, node_id)
            continue
        for key in sorted(set(action) - ALLOWED_ACTION_KEYS):
            state.error("SCHEMA_INVALID", f"Unknown action field: {key}", f"{action_path}.{key}", node_id)
        action_key = _text(action.get("key"))
        action_type = _text(action.get("type")) or _text(action.get("action"))
        if not action_key or not _STABLE_KEY_PATTERN.fullmatch(action_key):
            state.error("SCHEMA_INVALID", "action.key must be a stable identifier", f"{action_path}.key", node_id)
        elif action_key in keys:
            state.error("DUPLICATE_ACTION_KEY", f"Duplicate action key: {action_key}", f"{action_path}.key", node_id)
        else:
            keys.add(action_key)
        if not action_type:
            state.error("SCHEMA_INVALID", "action.type is required", f"{action_path}.type", node_id)
        elif action_type not in ALLOWED_ACTION_TYPES:
            state.error("ACTION_NOT_FOUND", f"Unsupported action type: {action_type}", f"{action_path}.type", node_id)
        if "target" in action and action.get("target") is not None and (
            not isinstance(action.get("target"), str) or not _STABLE_KEY_PATTERN.fullmatch(action["target"].strip())
        ):
            state.error("SCHEMA_INVALID", "action.target must be a stable identifier", f"{action_path}.target", node_id)
        if "params" in action and not isinstance(action.get("params"), dict):
            state.error("SCHEMA_INVALID", "action.params must be an object", f"{action_path}.params", node_id)
    return keys


def _validate_events(
    events: Any,
    path: str,
    node_id: str | None,
    action_keys: set[str],
    state: ValidationState,
) -> None:
    if events is None:
        return
    if not isinstance(events, list):
        return
    for index, event in enumerate(events):
        event_path = f"{path}[{index}]"
        if not isinstance(event, dict):
            state.error("SCHEMA_INVALID", "event must be an object", event_path, node_id)
            continue
        for key in sorted(set(event) - ALLOWED_EVENT_KEYS):
            state.error("SCHEMA_INVALID", f"Unknown event field: {key}", f"{event_path}.{key}", node_id)
        event_name = _text(event.get("event")) or _text(event.get("name")) or _text(event.get("type"))
        if not event_name or not _STABLE_KEY_PATTERN.fullmatch(event_name):
            state.error("SCHEMA_INVALID", "event must declare a stable event name", event_path, node_id)
        action_ref = _text(event.get("actionRef")) or _text(event.get("action"))
        if not action_ref or not _STABLE_KEY_PATTERN.fullmatch(action_ref):
            state.error("SCHEMA_INVALID", "event must declare a stable actionRef/action", event_path, node_id)
        elif action_ref not in action_keys:
            state.error("ACTION_REFERENCE_NOT_FOUND", f"Event references an undeclared action: {action_ref}", event_path, node_id)


def _scan_security(value: Any, path: str, node_id: str | None, state: ValidationState) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            normalized_key = re.sub(r"[^A-Za-z0-9]", "", str(key)).lower()
            child_path = f"{path}.{key}"
            if normalized_key in FORBIDDEN_NORMALIZED_KEYS:
                state.error(
                    "SECURITY_VIOLATION",
                    f"Forbidden configuration key: {key}",
                    child_path,
                    node_id,
                    recoverable=False,
                )
            _scan_security(child, child_path, node_id, state)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            _scan_security(child, f"{path}[{index}]", node_id, state)
    elif isinstance(value, str):
        for pattern in _DANGEROUS_STRING_PATTERNS:
            if pattern.search(value):
                state.error(
                    "SECURITY_VIOLATION",
                    "Executable code, arbitrary URLs, and dangerous expressions are forbidden",
                    path,
                    node_id,
                    recoverable=False,
                )
                break


def _bounded_json_value(value: Any, depth: int = 0) -> bool:
    """Allow only bounded declarative filter values, never executable objects."""

    if depth > 8:
        return False
    if value is None or isinstance(value, (str, int, bool)):
        return not isinstance(value, str) or len(value) <= 4096
    if isinstance(value, float):
        return value == value and value not in {float("inf"), float("-inf")}
    if isinstance(value, list):
        return len(value) <= 100 and all(_bounded_json_value(item, depth + 1) for item in value)
    if isinstance(value, dict):
        return len(value) <= 20 and all(
            isinstance(key, str)
            and re.fullmatch(r"[A-Za-z][A-Za-z0-9_.:-]{0,127}", key)
            and _bounded_json_value(child, depth + 1)
            for key, child in value.items()
        )
    return False


def _report(
    document: Any | None,
    state: ValidationState,
) -> dict[str, Any]:
    document_hash = render_document_hash(document)
    summary = (
        f"Validated {state.node_count} Render node(s): "
        f"{len(state.errors)} error(s), {len(state.warnings)} warning(s)."
    )
    return {
        "schemaVersion": "validation-report/v1",
        "valid": not state.errors,
        "rulesVersion": RULE_VERSION,
        "ruleVersion": RULE_VERSION,
        "documentHash": document_hash,
        # Kept for the stable ValidationReport schema. The component source is
        # now the frozen authoring skill rather than an online catalog response.
        "catalogRevision": COMPONENT_SKILL_REF,
        "protocolVersion": document.get("protocolVersion") if isinstance(document, dict) else None,
        "errors": state.errors,
        "warnings": state.warnings,
        "stats": {
            "nodeCount": state.node_count,
            "componentCount": len(state.component_keys),
            "maxDepth": state.max_depth,
            "errorCount": len(state.errors),
            "warningCount": len(state.warnings),
        },
        "summary": summary,
    }


def _issue(
    code: str,
    message: str,
    json_path: str,
    node_id: str | None,
    recoverable: bool,
    severity: str = "ERROR",
) -> dict[str, Any]:
    return {
        "code": code,
        "stage": "render-validation",
        "severity": severity,
        "message": message,
        "jsonPath": json_path,
        "nodeId": node_id,
        "repairable": recoverable,
        "recoverable": recoverable,
    }


def _text(value: Any) -> str | None:
    return value.strip() if isinstance(value, str) and value.strip() else None
