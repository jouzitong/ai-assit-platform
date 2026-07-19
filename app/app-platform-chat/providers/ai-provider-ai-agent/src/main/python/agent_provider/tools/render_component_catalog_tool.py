from __future__ import annotations

import json
import os
import re
from typing import Any

from agents import function_tool

from .platform_http import post_platform_json


DEFAULT_COMPONENT_CATALOG_URL = (
    "http://127.0.0.1:9764/render/internal/v1/render-components/catalog/query"
)
MAX_COMPONENTS = 100
DEFAULT_COMPONENT_LIMIT = 100
_SHA256_PATTERN = re.compile(r"^sha256:[0-9a-f]{64}$")
_VERSION_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._+-]{0,127}$")


def _component_catalog_url() -> str:
    return (os.getenv("AI_AGENT_RENDER_COMPONENT_CATALOG_URL") or DEFAULT_COMPONENT_CATALOG_URL).strip()


def fetch_component_catalog(
    run: dict[str, Any],
    *,
    component_keys: list[str] | None = None,
    keyword: str | None = None,
    category: str | None = None,
    limit: int = DEFAULT_COMPONENT_LIMIT,
    include_documentation: bool = False,
) -> dict[str, Any]:
    keys, key_errors = _normalize_component_keys(component_keys)
    if key_errors:
        return {
            "tool": "render_component_catalog_tool",
            "success": False,
            "errorCode": "COMPONENT_CATALOG_REQUEST_INVALID",
            "error": key_errors[0],
        }
    if isinstance(limit, bool):
        requested_limit = DEFAULT_COMPONENT_LIMIT
    elif isinstance(limit, int):
        requested_limit = limit
    elif isinstance(limit, str) and re.fullmatch(r"[0-9]+", limit.strip()):
        requested_limit = int(limit.strip())
    else:
        requested_limit = DEFAULT_COMPONENT_LIMIT
    safe_limit = DEFAULT_COMPONENT_LIMIT if requested_limit < 1 else min(requested_limit, MAX_COMPONENTS)
    payload: dict[str, Any] = {"componentKeys": keys, "limit": safe_limit}
    if isinstance(keyword, str) and keyword.strip():
        payload["keyword"] = keyword.strip()[:128]
    if isinstance(category, str) and category.strip():
        payload["category"] = category.strip()[:64]

    result = post_platform_json(
        _component_catalog_url(),
        payload,
        token_env_keys=(
            "AI_AGENT_RENDER_COMPONENT_CATALOG_TOKEN",
            "AI_AGENT_PLATFORM_TOKEN",
            "AI_AGENT_KB_SEARCH_TOKEN",
        ),
        trace_id=_run_text(run, "traceId"),
        run_id=_run_text(run, "runId"),
    )
    if not result.get("success"):
        return {"tool": "render_component_catalog_tool", **result}

    data = result.get("data") if isinstance(result.get("data"), dict) else {}
    catalog_revision = _text(data.get("catalogRevision"))
    if not catalog_revision:
        return {
            "tool": "render_component_catalog_tool",
            "success": False,
            "errorCode": "COMPONENT_CATALOG_REVISION_MISSING",
            "error": "Published component catalog did not return a catalog revision.",
        }
    if not _SHA256_PATTERN.fullmatch(catalog_revision):
        return {
            "tool": "render_component_catalog_tool",
            "success": False,
            "errorCode": "COMPONENT_CATALOG_REVISION_INVALID",
            "error": "Published component catalog returned an invalid catalog revision.",
        }
    raw_components = data.get("components")
    if not isinstance(raw_components, list):
        return {
            "tool": "render_component_catalog_tool",
            "success": False,
            "errorCode": "COMPONENT_CATALOG_RESPONSE_INVALID",
            "error": "Published component catalog did not return a components array.",
        }
    if len(raw_components) > MAX_COMPONENTS:
        return {
            "tool": "render_component_catalog_tool",
            "success": False,
            "errorCode": "COMPONENT_CATALOG_RESPONSE_INVALID",
            "error": f"Published component catalog returned more than {MAX_COMPONENTS} components.",
        }
    components: list[dict[str, Any]] = []
    seen_keys: set[str] = set()
    for item in raw_components:
        if not isinstance(item, dict):
            return {
                "tool": "render_component_catalog_tool",
                "success": False,
                "errorCode": "COMPONENT_CATALOG_RESPONSE_INVALID",
                "error": "Published component catalog contains a non-object component.",
            }
        component_key = _text(item.get("componentKey")) or _text(item.get("key"))
        source_revision = _text(item.get("sourceRevision"))
        if (
            not component_key
            or not re.fullmatch(r"[A-Za-z][A-Za-z0-9_.:-]{0,127}", component_key)
            or not source_revision
            or not _SHA256_PATTERN.fullmatch(source_revision)
            or component_key in seen_keys
        ):
            return {
                "tool": "render_component_catalog_tool",
                "success": False,
                "errorCode": "COMPONENT_CATALOG_RESPONSE_INVALID",
                "error": "Every published component must have a unique componentKey and sourceRevision.",
            }
        seen_keys.add(component_key)
        normalized_component = normalize_component_contract(
            item,
            include_documentation=include_documentation,
        )
        if not _text(normalized_component.get("componentVersion")):
            return {
                "tool": "render_component_catalog_tool",
                "success": False,
                "errorCode": "COMPONENT_VERSION_MISSING",
                "error": f"Published component has no verifiable componentVersion: {component_key}",
            }
        if normalized_component.get("contractAvailable") is not True:
            return {
                "tool": "render_component_catalog_tool",
                "success": False,
                "errorCode": "COMPONENT_CONTRACT_UNAVAILABLE",
                "error": f"Published component has no machine-readable contract: {component_key}",
            }
        components.append(normalized_component)
    components.sort(key=lambda item: item.get("componentKey") or item.get("key") or "")
    return {
        "tool": "render_component_catalog_tool",
        "success": True,
        "catalogRevision": catalog_revision,
        "components": components,
        "count": len(components),
        "summary": f"Returned {len(components)} published Render component contract(s).",
    }


def build_render_component_catalog_tool(run: dict[str, Any], function_tool_factory: Any) -> Any:
    def query_render_components(
        component_keys_json: str = "[]",
        keyword: str = "",
        category: str = "",
        limit: int = DEFAULT_COMPONENT_LIMIT,
    ) -> dict[str, Any]:
        """Read live published Render component versions, props, events, constraints, and examples."""

        keys, error_message = parse_component_keys_json(component_keys_json)
        if error_message:
            return {
                "tool": "render_component_catalog_tool",
                "success": False,
                "errorCode": "COMPONENT_CATALOG_REQUEST_INVALID",
                "error": error_message,
            }
        return fetch_component_catalog(
            run,
            component_keys=keys,
            keyword=keyword,
            category=category,
            limit=limit,
        )

    decorator = function_tool_factory(
        name_override="render_component_catalog_tool",
        description_override=(
            "Read the live catalog of published Render components. Pass component_keys_json as a JSON array of exact keys, "
            "or use keyword/category discovery. Returned contracts are parsed from the persisted component asset."
        ),
    )
    return decorator(query_render_components)


@function_tool
def render_component_catalog_tool(
    component_keys_json: str = "[]",
    keyword: str = "",
    category: str = "",
    limit: int = DEFAULT_COMPONENT_LIMIT,
) -> dict[str, Any]:
    """Read live published Render component contracts."""

    keys, error_message = parse_component_keys_json(component_keys_json)
    if error_message:
        return {
            "tool": "render_component_catalog_tool",
            "success": False,
            "errorCode": "COMPONENT_CATALOG_REQUEST_INVALID",
            "error": error_message,
        }
    return fetch_component_catalog({}, component_keys=keys, keyword=keyword, category=category, limit=limit)


def parse_component_keys_json(value: str | list[str] | None) -> tuple[list[str], str | None]:
    if value is None:
        return [], None
    if isinstance(value, list):
        decoded: Any = value
    elif isinstance(value, str):
        if not value.strip():
            return [], None
        try:
            decoded = json.loads(value)
        except json.JSONDecodeError as exc:
            return [], f"component_keys_json is invalid JSON: {exc.msg}"
    else:
        return [], "component_keys_json must be a JSON array"
    if not isinstance(decoded, list):
        return [], "component_keys_json must be a JSON array"
    keys, errors = _normalize_component_keys(decoded)
    return keys, errors[0] if errors else None


def normalize_component_contract(item: dict[str, Any], *, include_documentation: bool = False) -> dict[str, Any]:
    key = _text(item.get("componentKey")) or _text(item.get("key")) or ""
    doc_markdown = item.get("docMarkdown") if isinstance(item.get("docMarkdown"), str) else ""
    example_json = item.get("exampleJson") if isinstance(item.get("exampleJson"), str) else ""
    envelope = _parse_asset_envelope(example_json)
    parameters = _parse_parameter_contract(doc_markdown, envelope)
    events = _parse_event_contract(doc_markdown)
    component_version = (
        _text(item.get("componentVersion"))
        or _nested_text(envelope, "sourceComponent", "version")
    )
    if component_version and not _VERSION_PATTERN.fullmatch(component_version):
        component_version = None
    example = _component_example(envelope, key)
    normalized: dict[str, Any] = {
        "key": key,
        "componentKey": key,
        "name": _text(item.get("name")) or key,
        "category": _text(item.get("category")),
        "componentVersion": component_version,
        "sourceRevision": _text(item.get("sourceRevision")),
        "updatedAt": item.get("updatedAt"),
        "parameters": parameters,
        "events": events,
        "example": example,
        "contractAvailable": bool(envelope) and _has_parameter_contract(doc_markdown),
    }
    asset = envelope.get("asset") if isinstance(envelope.get("asset"), dict) else {}
    limitations = _text(asset.get("limitations"))
    normalized["sourceComponentKey"] = _nested_text(envelope, "sourceComponent", "key")
    normalized["summary"] = _text(asset.get("summary"))
    normalized["useCases"] = [
        value.strip()
        for value in asset.get("useCases", [])
        if isinstance(value, str) and value.strip()
    ] if isinstance(asset.get("useCases"), list) else []
    normalized["usageGuide"] = _text(asset.get("usageGuide"))
    normalized["limitations"] = limitations
    normalized["constraints"] = [limitations] if limitations else []
    normalized["tags"] = [
        value.strip()
        for value in asset.get("tags", [])
        if isinstance(value, str) and value.strip()
    ] if isinstance(asset.get("tags"), list) else []
    if include_documentation:
        normalized["documentation"] = doc_markdown
    return normalized


def _parse_asset_envelope(value: str) -> dict[str, Any]:
    if not value.strip():
        return {}
    try:
        decoded = json.loads(value)
    except json.JSONDecodeError:
        return {}
    if not isinstance(decoded, dict) or decoded.get("schemaVersion") != "component-asset/v1":
        return {}
    return decoded


def _parse_parameter_contract(markdown: str, envelope: dict[str, Any]) -> list[dict[str, Any]]:
    rows = _markdown_table_rows(markdown, "## 4. 参数契约", "## 5.")
    props = envelope.get("props") if isinstance(envelope.get("props"), dict) else {}
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    for columns in rows:
        if len(columns) < 6:
            continue
        key = columns[0].strip()
        if not key or key in seen or key in {"参数", "---"}:
            continue
        # The persisted asset document includes all source-component props,
        # while column four marks which props are actually part of this
        # published asset contract.
        if columns[3].strip().lower() not in {"是", "yes", "true", "required"}:
            continue
        seen.add(key)
        parameter: dict[str, Any] = {
            "key": key,
            "type": columns[1].strip(),
            "required": columns[2].strip().lower() in {"是", "yes", "true", "required"},
            "description": columns[5].replace("<br>", "\n").strip(),
        }
        if key in props:
            parameter["example"] = props[key]
        else:
            parsed_default = _parse_markdown_json(columns[4])
            if parsed_default is not _MISSING:
                parameter["example"] = parsed_default
        result.append(parameter)
    return result


def _parse_event_contract(markdown: str) -> list[dict[str, str]]:
    rows = _markdown_table_rows(markdown, "## 6. 事件契约", "## 7.")
    result: list[dict[str, str]] = []
    seen: set[str] = set()
    for columns in rows:
        if len(columns) < 2:
            continue
        name = columns[0].strip()
        if not name or name in {"-", "事件", "---"} or name in seen:
            continue
        seen.add(name)
        result.append({"name": name, "description": columns[1].replace("<br>", "\n").strip()})
    return result


def _markdown_table_rows(markdown: str, start_heading: str, end_heading_prefix: str) -> list[list[str]]:
    if not markdown:
        return []
    start = markdown.find(start_heading)
    if start < 0:
        return []
    body = markdown[start + len(start_heading):]
    end = body.find(end_heading_prefix)
    if end >= 0:
        body = body[:end]
    rows: list[list[str]] = []
    for line in body.splitlines():
        stripped = line.strip()
        if not stripped.startswith("|") or not stripped.endswith("|"):
            continue
        columns = _split_markdown_row(stripped[1:-1])
        if columns and all(re.fullmatch(r"\s*:?-{3,}:?\s*", column or "") for column in columns):
            continue
        rows.append(columns)
    return rows[1:] if rows else []


def _split_markdown_row(value: str) -> list[str]:
    columns: list[str] = []
    buffer: list[str] = []
    escaped = False
    for character in value:
        if escaped:
            buffer.append(character)
            escaped = False
        elif character == "\\":
            escaped = True
        elif character == "|":
            columns.append("".join(buffer).strip())
            buffer = []
        else:
            buffer.append(character)
    if escaped:
        buffer.append("\\")
    columns.append("".join(buffer).strip())
    return columns


def _component_example(envelope: dict[str, Any], component_key: str) -> dict[str, Any]:
    props = envelope.get("props") if isinstance(envelope.get("props"), dict) else {}
    source_key = _nested_text(envelope, "sourceComponent", "key") or component_key
    # The catalog key is the only renderer identity that validation may trust.
    # The source component can be an Application implementation alias.
    return {"component": component_key or source_key, "props": props}


def _normalize_component_keys(values: list[Any] | None) -> tuple[list[str], list[str]]:
    result: list[str] = []
    errors: list[str] = []
    seen: set[str] = set()
    for raw in values or []:
        if not isinstance(raw, str) or not raw.strip():
            errors.append("component keys must be non-empty strings")
            continue
        key = raw.strip()
        if len(key) > 128 or not re.fullmatch(r"[A-Za-z][A-Za-z0-9_.:-]*", key):
            errors.append(f"invalid component key: {key[:128]}")
            continue
        if key not in seen:
            seen.add(key)
            result.append(key)
    if len(result) > MAX_COMPONENTS:
        errors.append(f"at most {MAX_COMPONENTS} component keys are allowed")
    return result[:MAX_COMPONENTS], errors


def _has_parameter_section(markdown: str) -> bool:
    return "## 4. 参数契约" in markdown


def _has_parameter_contract(markdown: str) -> bool:
    """Return true only when the generated parameter table has a header and separator."""

    if not _has_parameter_section(markdown):
        return False
    start = markdown.find("## 4. 参数契约") + len("## 4. 参数契约")
    body = markdown[start:]
    end = body.find("## 5.")
    if end >= 0:
        body = body[:end]
    rows = [
        _split_markdown_row(line.strip()[1:-1])
        for line in body.splitlines()
        if line.strip().startswith("|") and line.strip().endswith("|")
    ]
    if len(rows) < 2:
        return False
    header = [column.strip() for column in rows[0]]
    separator = rows[1]
    if len(header) < 6 or header[:3] != ["参数", "类型", "必填"]:
        return False
    if not (len(separator) >= 6 and all(
        re.fullmatch(r"\s*:?-{3,}:?\s*", column or "") for column in separator
    )):
        return False
    for row in rows[2:]:
        if len(row) < 6 or not row[0].strip() or not row[1].strip():
            return False
    return True


def _parse_markdown_json(value: str) -> Any:
    normalized = value.replace("<br>", "\n").strip()
    if not normalized or normalized == "-":
        return _MISSING
    try:
        return json.loads(normalized)
    except json.JSONDecodeError:
        return normalized


def _nested_text(value: dict[str, Any], parent: str, key: str) -> str | None:
    nested = value.get(parent)
    return _text(nested.get(key)) if isinstance(nested, dict) else None


def _text(value: Any) -> str | None:
    return value.strip() if isinstance(value, str) and value.strip() else None


def _run_text(run: dict[str, Any], key: str) -> str | None:
    return _text(run.get(key)) if isinstance(run, dict) else None


_MISSING = object()
