from __future__ import annotations

import json
import re
from datetime import datetime
from typing import Any

from agents import function_tool

from agent_provider.chat_endpoint import chat_endpoint

from .platform_http import post_platform_json


DATA_PREVIEW_CHAT_ROUTE = "/internal/v1/ai/agent-tools/data-preview/query"
MAX_PREVIEW_ROWS = 100
MAX_CONTRACT_BYTES = 256 * 1024
_IDENTIFIER = re.compile(r"^[A-Za-z_][A-Za-z0-9_]{0,63}(?:\.[A-Za-z_][A-Za-z0-9_]{0,63})?$")
_MODEL_IDENTIFIER = re.compile(r"^[A-Za-z_][A-Za-z0-9_]{0,63}$")
_SOURCE_REVISION = re.compile(r"^(?:virtual-model/)?v[1-9][0-9]*$")
_AGGREGATIONS = {"COUNT", "SUM", "MIN", "MAX", "AVG"}
_FILTER_OPERATORS = {
    "EQ", "NE", "GT", "GTE", "LT", "LTE", "IN", "NOT_IN",
    "IS_NULL", "IS_NOT_NULL", "LIKE", "STARTS_WITH", "ENDS_WITH",
}
_PRESETS = {
    "LAST_7_DAYS", "LAST_30_DAYS", "LAST_3_MONTHS", "LAST_6_MONTHS",
    "LAST_12_MONTHS", "THIS_MONTH", "THIS_YEAR",
}
_DATA_CONTRACT_SCHEMA_VERSION = "data-contract/v1"
_TOP_LEVEL_KEYS = {
    "schemaVersion", "model", "catalogVersion", "measures", "dimensions",
    "timeRange", "filters", "sorts", "sourceRevision", "assumptions",
}
_MEASURE_KEYS = {"field", "aggregation", "label", "alias"}
_DIMENSION_KEYS = {"field", "label", "alias"}
_FILTER_KEYS = {"field", "operator", "value", "values"}
_TIME_RANGE_KEYS = {"field", "preset", "start", "end"}
_SORT_KEYS = {"field", "direction"}
MAX_FILTER_VALUES = 100
MAX_VALUE_BYTES = 32 * 1024
_FORBIDDEN_KEYS = {
    "sql", "statement", "table", "physicaltable", "datasourceurl", "url", "endpoint",
    "headers", "authorization", "credential", "credentials", "password", "secret",
    "token", "apikey", "proto", "prototype", "constructor",
}


def _data_preview_url() -> str:
    return chat_endpoint(DATA_PREVIEW_CHAT_ROUTE)


def validate_data_contract(value: str | dict[str, Any], limit: int = 20) -> tuple[dict[str, Any] | None, list[dict[str, str]]]:
    """Validate the Agent-owned DataContract before it reaches Java."""

    errors: list[dict[str, str]] = []
    if isinstance(value, str):
        if not value.strip():
            return None, [_error("DATA_CONTRACT_REQUIRED", "$", "data_contract is required")]
        if len(value.encode("utf-8")) > MAX_CONTRACT_BYTES:
            return None, [_error("DATA_CONTRACT_TOO_LARGE", "$", "data_contract exceeds 256 KiB")]
        try:
            decoded = json.loads(
                value,
                parse_constant=_reject_json_constant,
                object_pairs_hook=_unique_json_object,
            )
        except json.JSONDecodeError as exc:
            return None, [_error(
                "DATA_CONTRACT_JSON_INVALID",
                "$",
                f"Invalid JSON at line {exc.lineno}, column {exc.colno}: {exc.msg}",
            )]
        except ValueError as exc:
            return None, [_error("DATA_CONTRACT_JSON_INVALID", "$", f"Invalid JSON: {exc}")]
    else:
        decoded = value

    if not isinstance(decoded, dict):
        return None, [_error("DATA_CONTRACT_SCHEMA_INVALID", "$", "DataContract must be a JSON object")]
    try:
        encoded_contract = json.dumps(
            decoded,
            ensure_ascii=False,
            separators=(",", ":"),
            allow_nan=False,
        ).encode("utf-8")
    except (TypeError, ValueError, RecursionError) as exc:
        return None, [_error("DATA_CONTRACT_SCHEMA_INVALID", "$", f"DataContract must be JSON-serializable: {exc}")]
    if len(encoded_contract) > MAX_CONTRACT_BYTES:
        return None, [_error("DATA_CONTRACT_TOO_LARGE", "$", "data_contract exceeds 256 KiB")]

    _detect_forbidden_keys(decoded, "$", errors)
    _reject_unknown_keys(decoded, _TOP_LEVEL_KEYS, "$", errors)
    schema_version = decoded.get("schemaVersion", _DATA_CONTRACT_SCHEMA_VERSION)
    if schema_version != _DATA_CONTRACT_SCHEMA_VERSION:
        errors.append(_error(
            "DATA_CONTRACT_SCHEMA_INVALID",
            "$.schemaVersion",
            f"Unsupported DataContract schemaVersion: {schema_version}",
        ))
    model = _required_model(decoded.get("model"), "$.model", errors)
    source_revision = _optional_text(decoded.get("sourceRevision"), "$.sourceRevision", 128, errors)
    if not source_revision:
        errors.append(_error(
            "DATA_CONTRACT_SOURCE_REVISION_REQUIRED",
            "$.sourceRevision",
            "sourceRevision is required for an auditable preview",
        ))
    elif not _SOURCE_REVISION.fullmatch(source_revision):
        errors.append(_error(
            "DATA_CONTRACT_SOURCE_REVISION_INVALID",
            "$.sourceRevision",
            "sourceRevision must use virtual-model/v{version} format",
        ))
    elif re.fullmatch(r"v[1-9][0-9]*", source_revision):
        source_revision = f"virtual-model/{source_revision}"
    catalog_version = _optional_positive_int(decoded.get("catalogVersion"), "$.catalogVersion", errors)
    measures = _normalize_measures(decoded.get("measures"), errors)
    dimensions = _normalize_dimensions(decoded.get("dimensions"), errors)
    filters = _normalize_filters(decoded.get("filters"), errors)
    time_range = _normalize_time_range(decoded.get("timeRange"), errors)
    sorts = _normalize_sorts(decoded.get("sorts"), errors)
    assumptions = _normalize_assumptions(decoded.get("assumptions", []), errors)

    if not measures and not dimensions:
        errors.append(_error(
            "DATA_CONTRACT_FIELDS_REQUIRED",
            "$",
            "At least one measure or dimension is required for preview",
        ))

    if errors:
        return None, errors
    normalized: dict[str, Any] = {
        "schemaVersion": _DATA_CONTRACT_SCHEMA_VERSION,
        "model": model,
        "measures": measures,
        "dimensions": dimensions,
        "filters": filters,
        "assumptions": assumptions,
    }
    if source_revision:
        normalized["sourceRevision"] = source_revision
    if catalog_version is not None:
        normalized["catalogVersion"] = catalog_version
    if time_range:
        normalized["timeRange"] = time_range
    if sorts:
        normalized["sorts"] = sorts
    return normalized, []


def preview_data_contract(run: dict[str, Any], data_contract: str | dict[str, Any], limit: int = 20) -> dict[str, Any]:
    normalized, errors = validate_data_contract(data_contract, limit)
    if errors:
        return {
            "tool": "data_preview_query_tool",
            "success": False,
            "errorCode": "DATA_CONTRACT_INVALID",
            "error": "DataContract validation failed",
            "errors": errors,
        }

    requested_limit = _normalize_limit(limit)
    execution_payload = {
        key: value
        for key, value in (normalized or {}).items()
        if key not in {"schemaVersion", "assumptions"}
    }
    execution_payload["limit"] = requested_limit
    result = post_platform_json(
        _data_preview_url(),
        execution_payload,
        token_env_keys=(
            "AI_AGENT_DATA_PREVIEW_TOKEN",
            "AI_AGENT_PLATFORM_TOKEN",
            "AI_AGENT_KB_SEARCH_TOKEN",
        ),
        trace_id=_run_text(run, "traceId"),
        run_id=_run_text(run, "runId"),
    )
    if not result.get("success"):
        return {"tool": "data_preview_query_tool", **result}

    data = result.get("data") if isinstance(result.get("data"), dict) else None
    if data is None:
        return {
            "tool": "data_preview_query_tool",
            "success": False,
            "errorCode": "DATA_PREVIEW_RESPONSE_INVALID",
            "error": "Data preview response data must be an object.",
        }
    records = data.get("records") if isinstance(data.get("records"), list) else []
    model = _optional_text(data.get("model"), "$.model", 128, [])
    source_response = _optional_text(data.get("sourceRevision"), "$.sourceRevision", 128, [])
    query_type = _optional_text(data.get("queryType"), "$.queryType", 32, [])
    catalog_version = data.get("catalogVersion")
    expected_query_type = "AGGREGATE" if (normalized or {}).get("measures") else "LIST"
    if (
        not model
        or model != (normalized or {}).get("model")
        or not source_response
        or source_response != (normalized or {}).get("sourceRevision")
        or query_type not in {"LIST", "AGGREGATE"}
        or query_type != expected_query_type
        or isinstance(catalog_version, bool)
        or not isinstance(catalog_version, int)
        or catalog_version < 1
        or not isinstance(data.get("columns"), list)
        or not isinstance(data.get("records"), list)
        or len(records) > MAX_PREVIEW_ROWS
        or any(not isinstance(row, dict) for row in records)
    ):
        return {
            "tool": "data_preview_query_tool",
            "success": False,
            "errorCode": "DATA_PREVIEW_RESPONSE_INVALID",
            "error": "Data preview response did not contain a matching bounded preview proof.",
        }
    return {
        "tool": "data_preview_query_tool",
        "success": True,
        "model": model,
        "catalogVersion": catalog_version,
        "sourceRevision": source_response,
        "queryType": query_type,
        "columns": data.get("columns") if isinstance(data.get("columns"), list) else [],
        "records": records,
        "total": data.get("total"),
        "truncated": bool(data.get("truncated")),
        "requestId": data.get("requestId"),
        "executionMs": data.get("executionMs"),
        "summary": f"Returned {len(records)} controlled preview row(s).",
        "assumptions": (normalized or {}).get("assumptions", []),
        "limit": requested_limit,
    }


def build_data_preview_query_tool(run: dict[str, Any], function_tool_factory: Any) -> Any:
    """Create a per-run preview Tool so trace and user-scoped bearer context are preserved."""

    def preview_data(data_contract: str, limit: int = 20) -> dict[str, Any]:
        """Validate a DataContract through a limited, read-only virtual-data preview query."""

        return preview_data_contract(run, data_contract, limit)

    decorator = function_tool_factory(
        name_override="data_preview_query_tool",
        description_override=(
            "Validate one DataContract against the published virtual catalog and return at most 100 read-only sample rows. "
            "Pass the complete DataContract as a JSON string; SQL, physical tables, credentials, and arbitrary endpoints are forbidden."
        ),
    )
    return decorator(preview_data)


@function_tool
def data_preview_query_tool(data_contract: str, limit: int = 20) -> dict[str, Any]:
    """Validate one DataContract with a controlled, read-only virtual-data preview."""

    return preview_data_contract({}, data_contract, limit)


def _normalize_measures(value: Any, errors: list[dict[str, str]]) -> list[dict[str, Any]]:
    if value is None:
        return []
    if not isinstance(value, list) or len(value) > 20:
        errors.append(_error("DATA_CONTRACT_SCHEMA_INVALID", "$.measures", "measures must be an array with at most 20 items"))
        return []
    result: list[dict[str, Any]] = []
    for index, item in enumerate(value):
        path = f"$.measures[{index}]"
        if not isinstance(item, dict):
            errors.append(_error("DATA_CONTRACT_SCHEMA_INVALID", path, "measure must be an object"))
            continue
        _reject_unknown_keys(item, _MEASURE_KEYS, path, errors)
        field = _required_identifier(item.get("field"), f"{path}.field", errors)
        aggregation = str(item.get("aggregation") or "").strip().lower()
        if aggregation.upper() not in _AGGREGATIONS:
            errors.append(_error("DATA_CONTRACT_AGGREGATION_INVALID", f"{path}.aggregation", f"Unsupported aggregation: {aggregation or '<empty>'}"))
        normalized = {"field": field, "aggregation": aggregation}
        _copy_optional_label_alias(item, normalized, path, errors)
        result.append(normalized)
    return result


def _normalize_dimensions(value: Any, errors: list[dict[str, str]]) -> list[dict[str, Any]]:
    if value is None:
        return []
    if not isinstance(value, list) or len(value) > 20:
        errors.append(_error("DATA_CONTRACT_SCHEMA_INVALID", "$.dimensions", "dimensions must be an array with at most 20 items"))
        return []
    result: list[dict[str, Any]] = []
    for index, item in enumerate(value):
        path = f"$.dimensions[{index}]"
        if not isinstance(item, dict):
            errors.append(_error("DATA_CONTRACT_SCHEMA_INVALID", path, "dimension must be an object"))
            continue
        _reject_unknown_keys(item, _DIMENSION_KEYS, path, errors)
        normalized = {"field": _required_identifier(item.get("field"), f"{path}.field", errors)}
        _copy_optional_label_alias(item, normalized, path, errors)
        result.append(normalized)
    return result


def _normalize_filters(value: Any, errors: list[dict[str, str]]) -> list[dict[str, Any]]:
    if value is None:
        return []
    if not isinstance(value, list) or len(value) > 50:
        errors.append(_error("DATA_CONTRACT_SCHEMA_INVALID", "$.filters", "filters must be an array with at most 50 items"))
        return []
    result: list[dict[str, Any]] = []
    for index, item in enumerate(value):
        path = f"$.filters[{index}]"
        if not isinstance(item, dict):
            errors.append(_error("DATA_CONTRACT_SCHEMA_INVALID", path, "filter must be an object"))
            continue
        _reject_unknown_keys(item, _FILTER_KEYS, path, errors)
        field = _required_identifier(item.get("field"), f"{path}.field", errors)
        operator = str(item.get("operator") or "EQ").strip().lower()
        if operator.upper() not in _FILTER_OPERATORS:
            errors.append(_error("DATA_CONTRACT_FILTER_INVALID", f"{path}.operator", f"Unsupported filter operator: {operator}"))
        normalized: dict[str, Any] = {"field": field, "operator": operator}
        if operator.upper() in {"IN", "NOT_IN"}:
            if "value" in item:
                errors.append(_error("DATA_CONTRACT_FILTER_INVALID", f"{path}.value", f"{operator} accepts values only"))
            values = item.get("values")
            if not isinstance(values, list) or not values or len(values) > MAX_FILTER_VALUES:
                errors.append(_error("DATA_CONTRACT_FILTER_INVALID", f"{path}.values", f"{operator} requires a non-empty values array"))
            else:
                _validate_value_size(values, f"{path}.values", errors)
                normalized["values"] = values
        elif operator.upper() in {"IS_NULL", "IS_NOT_NULL"}:
            if "value" in item or "values" in item:
                errors.append(_error("DATA_CONTRACT_FILTER_INVALID", path, f"{operator} does not accept value/values"))
        else:
            if "values" in item:
                errors.append(_error("DATA_CONTRACT_FILTER_INVALID", f"{path}.values", f"{operator} accepts value only"))
            if "value" not in item:
                errors.append(_error("DATA_CONTRACT_FILTER_INVALID", f"{path}.value", f"{operator} requires value"))
            else:
                _validate_value_size(item.get("value"), f"{path}.value", errors)
                normalized["value"] = item.get("value")
        result.append(normalized)
    return result


def _normalize_time_range(value: Any, errors: list[dict[str, str]]) -> dict[str, Any] | None:
    if value is None:
        return None
    if not isinstance(value, dict):
        errors.append(_error("DATA_CONTRACT_SCHEMA_INVALID", "$.timeRange", "timeRange must be an object"))
        return None
    _reject_unknown_keys(value, _TIME_RANGE_KEYS, "$.timeRange", errors)
    normalized: dict[str, Any] = {
        "field": _required_identifier(value.get("field"), "$.timeRange.field", errors),
    }
    if "timezone" in value:
        errors.append(_error(
            "DATA_CONTRACT_TIME_RANGE_INVALID",
            "$.timeRange.timezone",
            "timezone is not supported by the UTC preview runtime",
        ))
    preset = str(value.get("preset") or "").strip().upper()
    start = value.get("start")
    end = value.get("end")
    if preset:
        if preset not in _PRESETS:
            errors.append(_error("DATA_CONTRACT_TIME_RANGE_INVALID", "$.timeRange.preset", f"Unsupported preset: {preset}"))
        normalized["preset"] = preset
    parsed_start = _normalize_time_boundary(start, "$.timeRange.start", errors) if start is not None else None
    parsed_end = _normalize_time_boundary(end, "$.timeRange.end", errors) if end is not None else None
    if parsed_start is not None:
        normalized["start"] = parsed_start
    if parsed_end is not None:
        normalized["end"] = parsed_end
    if parsed_start is not None and parsed_end is not None:
        try:
            if datetime.fromisoformat(parsed_start.replace("Z", "+00:00")) >= datetime.fromisoformat(parsed_end.replace("Z", "+00:00")):
                errors.append(_error("DATA_CONTRACT_TIME_RANGE_INVALID", "$.timeRange", "timeRange start must be earlier than end"))
        except ValueError:
            pass
    if not preset and start is None and end is None:
        errors.append(_error("DATA_CONTRACT_TIME_RANGE_INVALID", "$.timeRange", "timeRange requires preset, start, or end"))
    return normalized


def _normalize_sorts(value: Any, errors: list[dict[str, str]]) -> list[dict[str, str]]:
    if value is None:
        return []
    if not isinstance(value, list) or len(value) > 10:
        errors.append(_error("DATA_CONTRACT_SCHEMA_INVALID", "$.sorts", "sorts must be an array with at most 10 items"))
        return []
    result: list[dict[str, str]] = []
    for index, item in enumerate(value):
        path = f"$.sorts[{index}]"
        if not isinstance(item, dict):
            errors.append(_error("DATA_CONTRACT_SCHEMA_INVALID", path, "sort must be an object"))
            continue
        _reject_unknown_keys(item, _SORT_KEYS, path, errors)
        direction = str(item.get("direction") or "ASC").strip().upper()
        if direction not in {"ASC", "DESC"}:
            errors.append(_error("DATA_CONTRACT_SORT_INVALID", f"{path}.direction", f"Unsupported sort direction: {direction}"))
        result.append({
            "field": _required_identifier(item.get("field"), f"{path}.field", errors),
            "direction": direction,
        })
    return result


def _normalize_assumptions(value: Any, errors: list[dict[str, str]]) -> list[str]:
    if value is None:
        return []
    if not isinstance(value, list) or len(value) > 50:
        errors.append(_error(
            "DATA_CONTRACT_SCHEMA_INVALID",
            "$.assumptions",
            "assumptions must be an array with at most 50 items",
        ))
        return []
    result: list[str] = []
    for index, item in enumerate(value):
        if not isinstance(item, str) or not item.strip() or len(item.strip()) > 512:
            errors.append(_error(
                "DATA_CONTRACT_SCHEMA_INVALID",
                f"$.assumptions[{index}]",
                "assumption must be non-empty text up to 512 characters",
            ))
            continue
        result.append(item.strip())
    return result


def _copy_optional_label_alias(source: dict[str, Any], target: dict[str, Any], path: str, errors: list[dict[str, str]]) -> None:
    label = _optional_text(source.get("label"), f"{path}.label", 128, errors)
    alias = _optional_text(source.get("alias"), f"{path}.alias", 64, errors)
    if label:
        target["label"] = label
    if alias:
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]{0,63}", alias):
            errors.append(_error("DATA_CONTRACT_ALIAS_INVALID", f"{path}.alias", "alias must be a simple identifier"))
        target["alias"] = alias


def _required_identifier(value: Any, path: str, errors: list[dict[str, str]]) -> str:
    normalized = str(value or "").strip()
    if not _IDENTIFIER.fullmatch(normalized):
        errors.append(_error("DATA_CONTRACT_FIELD_INVALID", path, "Expected a virtual identifier (field or relation.field)"))
    return normalized


def _required_model(value: Any, path: str, errors: list[dict[str, str]]) -> str:
    normalized = str(value or "").strip()
    if not _MODEL_IDENTIFIER.fullmatch(normalized):
        errors.append(_error("DATA_CONTRACT_MODEL_INVALID", path, "model must be a simple virtual model identifier"))
    return normalized


def _optional_text(value: Any, path: str, max_length: int, errors: list[dict[str, str]]) -> str | None:
    if value is None:
        return None
    if not isinstance(value, str) or not value.strip() or len(value.strip()) > max_length:
        errors.append(_error("DATA_CONTRACT_SCHEMA_INVALID", path, f"Expected non-empty text up to {max_length} characters"))
        return None
    return value.strip()


def _optional_positive_int(value: Any, path: str, errors: list[dict[str, str]]) -> int | None:
    if value is None:
        return None
    if isinstance(value, bool):
        errors.append(_error("DATA_CONTRACT_SCHEMA_INVALID", path, "Expected a positive integer"))
        return None
    if isinstance(value, int):
        parsed = value
    elif isinstance(value, str) and re.fullmatch(r"[0-9]+", value.strip()):
        parsed = int(value.strip())
    else:
        parsed = 0
    if parsed < 1:
        errors.append(_error("DATA_CONTRACT_SCHEMA_INVALID", path, "Expected a positive integer"))
        return None
    return parsed


def _detect_forbidden_keys(value: Any, path: str, errors: list[dict[str, str]]) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            normalized = str(key).replace("-", "").replace("_", "").lower()
            if normalized in _FORBIDDEN_KEYS:
                errors.append(_error("DATA_CONTRACT_SECURITY_VIOLATION", f"{path}.{key}", f"Forbidden DataContract key: {key}"))
            _detect_forbidden_keys(child, f"{path}.{key}", errors)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            _detect_forbidden_keys(child, f"{path}[{index}]", errors)


def _error(code: str, path: str, message: str) -> dict[str, str]:
    return {"code": code, "jsonPath": path, "message": message}


def _reject_json_constant(value: str) -> None:
    raise ValueError(f"non-standard numeric constant is forbidden: {value}")


def _unique_json_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate object key is forbidden: {key}")
        result[key] = value
    return result


def _reject_unknown_keys(
    value: dict[str, Any],
    allowed: set[str],
    path: str,
    errors: list[dict[str, str]],
) -> None:
    for key in sorted(set(value) - allowed):
        errors.append(_error(
            "DATA_CONTRACT_SCHEMA_INVALID",
            f"{path}.{key}",
            f"Unknown DataContract field: {key}",
        ))


def _validate_value_size(value: Any, path: str, errors: list[dict[str, str]]) -> None:
    if not _is_safe_json_value(value):
        errors.append(_error(
            "DATA_CONTRACT_SCHEMA_INVALID",
            path,
            "filter values must be JSON scalars or arrays of JSON scalars",
        ))
        return
    try:
        encoded = json.dumps(
            value,
            ensure_ascii=False,
            separators=(",", ":"),
            allow_nan=False,
        ).encode("utf-8")
    except (TypeError, ValueError, RecursionError):
        errors.append(_error("DATA_CONTRACT_SCHEMA_INVALID", path, "filter value must be JSON-serializable"))
        return
    if len(encoded) > MAX_VALUE_BYTES:
        errors.append(_error("DATA_CONTRACT_VALUE_TOO_LARGE", path, "filter value exceeds 32 KiB"))


def _is_safe_json_value(value: Any) -> bool:
    if value is None or isinstance(value, (str, int, float, bool)):
        return not isinstance(value, float) or value == value and value not in {float("inf"), float("-inf")}
    if isinstance(value, list):
        return len(value) <= MAX_FILTER_VALUES and all(_is_safe_json_value(item) for item in value)
    return False


def _normalize_time_boundary(value: Any, path: str, errors: list[dict[str, str]]) -> str | None:
    if not isinstance(value, str) or not value.strip() or len(value.strip()) > 64:
        errors.append(_error("DATA_CONTRACT_TIME_RANGE_INVALID", path, "time range boundaries must be non-empty ISO-8601 text"))
        return None
    text = value.strip()
    try:
        parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError:
        errors.append(_error("DATA_CONTRACT_TIME_RANGE_INVALID", path, "time range boundaries must be ISO-8601 date-time values"))
        return None
    if parsed.tzinfo is None:
        errors.append(_error("DATA_CONTRACT_TIME_RANGE_INVALID", path, "time range boundaries must include a UTC offset"))
    return text


def _normalize_limit(value: Any) -> int:
    if isinstance(value, bool):
        return 20
    if isinstance(value, int):
        return max(1, min(value, MAX_PREVIEW_ROWS))
    if isinstance(value, str) and re.fullmatch(r"[0-9]+", value.strip()):
        return max(1, min(int(value.strip()), MAX_PREVIEW_ROWS))
    return 20


def _run_text(run: dict[str, Any], key: str) -> str | None:
    value = run.get(key) if isinstance(run, dict) else None
    return value.strip() if isinstance(value, str) and value.strip() else None
