import json
import re
from typing import Any


def parse_render_json(render_json: str | dict[str, Any] | list[Any]) -> tuple[Any | None, list[str]]:
    if isinstance(render_json, (dict, list)):
        return render_json, []
    if not isinstance(render_json, str) or not render_json.strip():
        return None, ["render_json is required and must be a non-empty JSON string."]
    try:
        return json.loads(render_json), []
    except json.JSONDecodeError as exc:
        return None, [f"Invalid JSON at line {exc.lineno}, column {exc.colno}: {exc.msg}"]


def parse_json_content(content: str | dict[str, Any] | list[Any], field_name: str = "content") -> tuple[Any | None, list[str]]:
    if isinstance(content, (dict, list)):
        return content, []
    if not isinstance(content, str) or not content.strip():
        return None, [f"{field_name} is required and must be a non-empty JSON string."]
    try:
        return json.loads(content), []
    except json.JSONDecodeError as exc:
        return None, [f"Invalid JSON at line {exc.lineno}, column {exc.colno}: {exc.msg}"]


def _normalize_structure_template_text(template: str) -> str:
    normalized = template.strip()
    normalized = re.sub(r'([{,]\s*)([A-Za-z_][A-Za-z0-9_\-]*)(\s*:)', r'\1"\2"\3', normalized)
    normalized = re.sub(r'(:\s*)([A-Za-z_][A-Za-z0-9_\-]*)(\s*[,}\]])', r'\1"\2"\3', normalized)
    normalized = re.sub(r'(\[\s*)([A-Za-z_][A-Za-z0-9_\-]*)(\s*[,}\]])', r'\1"\2"\3', normalized)
    return normalized


def parse_structure_template(structure: str | dict[str, Any] | list[Any] | None) -> tuple[Any | None, list[str]]:
    if structure is None:
        return None, []
    if isinstance(structure, (dict, list)):
        return structure, []
    if not isinstance(structure, str) or not structure.strip():
        return None, ["structure semantics must be a non-empty string when provided."]

    raw = structure.strip()
    parse_attempts = [raw]
    normalized = _normalize_structure_template_text(raw)
    if normalized != raw:
        parse_attempts.append(normalized)

    last_error: json.JSONDecodeError | None = None
    for attempt in parse_attempts:
        try:
            return json.loads(attempt), []
        except json.JSONDecodeError as exc:
            last_error = exc

    if last_error is None:
        return None, ["structure semantics parse failed."]
    return None, [
        "structure semantics must be valid JSON or simplified JSON-like text "
        f"(for example {{a: {{b: [c, d]}}}}). Parse failed at line {last_error.lineno}, "
        f"column {last_error.colno}: {last_error.msg}"
    ]


def describe_expected_type(template: Any) -> str:
    if isinstance(template, dict):
        return "object"
    if isinstance(template, list):
        return "array"
    if template is None:
        return "null"
    if isinstance(template, bool):
        return "boolean"
    if isinstance(template, (int, float)) and not isinstance(template, bool):
        return "number"
    return "primitive"


def validate_json_structure(
    actual: Any,
    expected: Any,
    path: str,
    errors: list[str],
) -> None:
    if isinstance(expected, dict):
        if not isinstance(actual, dict):
            errors.append(f"{path}: expected object, got {type(actual).__name__}.")
            return

        expected_keys = list(expected.keys())
        actual_keys = set(actual.keys())
        for key in expected_keys:
            if key not in actual:
                errors.append(f"{path}.{key}: missing required key.")
                continue
            validate_json_structure(actual[key], expected[key], f"{path}.{key}", errors)

        extra_keys = sorted(actual_keys - set(expected_keys))
        for key in extra_keys:
            errors.append(f"{path}.{key}: unexpected key.")
        return

    if isinstance(expected, list):
        if not isinstance(actual, list):
            errors.append(f"{path}: expected array, got {type(actual).__name__}.")
            return

        if not expected:
            return

        if len(expected) == 1:
            item_template = expected[0]
            for index, item in enumerate(actual):
                validate_json_structure(item, item_template, f"{path}[{index}]", errors)
            return

        if len(actual) != len(expected):
            errors.append(f"{path}: expected array length {len(expected)}, got {len(actual)}.")

        for index, item_template in enumerate(expected):
            if index >= len(actual):
                errors.append(f"{path}[{index}]: missing required item.")
                continue
            validate_json_structure(actual[index], item_template, f"{path}[{index}]", errors)
        return

    if isinstance(actual, (dict, list)):
        errors.append(
            f"{path}: expected {describe_expected_type(expected)}, got {type(actual).__name__}."
        )


def analyze_structured_json(
    content: str | dict[str, Any] | list[Any],
    structure: str | dict[str, Any] | list[Any] | None,
) -> dict[str, Any]:
    parsed, parse_errors = parse_json_content(content)
    if parse_errors:
        return {
            "valid": False,
            "contentType": "json",
            "errors": parse_errors,
            "warnings": [],
            "summary": "JSON parse failed.",
        }

    expected, structure_errors = parse_structure_template(structure)
    if structure_errors:
        return {
            "valid": False,
            "contentType": "json",
            "errors": structure_errors,
            "warnings": [],
            "summary": "Structure semantics parse failed.",
            "normalized": parsed,
        }

    errors: list[str] = []
    warnings: list[str] = []

    if expected is not None:
        validate_json_structure(parsed, expected, "$", errors)
    else:
        warnings.append("No structure semantics configured; only JSON syntax was validated.")

    summary = (
        "JSON structure matched expected semantics."
        if not errors
        else f"JSON structure validation failed with {len(errors)} error(s)."
    )
    return {
        "valid": not errors,
        "contentType": "json",
        "errors": errors,
        "warnings": warnings,
        "summary": summary,
        "expectedStructure": expected,
        "normalized": parsed,
    }


def is_component_node(node: Any) -> bool:
    return isinstance(node, dict) and any(key in node for key in ("component", "type", "name"))


def node_label(node: dict[str, Any]) -> str:
    return str(node.get("component") or node.get("type") or node.get("name") or "unknown")


def walk_node(
    node: Any,
    path: str,
    errors: list[str],
    warnings: list[str],
    components: list[str],
    stats: dict[str, int],
) -> None:
    stats["visited"] += 1
    if isinstance(node, dict):
        stats["objectNodes"] += 1
        if is_component_node(node):
            stats["componentNodes"] += 1
            label = node_label(node)
            components.append(f"{path}:{label}")
            if not any(str(node.get(key) or "").strip() for key in ("component", "type", "name")):
                errors.append(f"{path}: component node must define component/type/name.")

        children = node.get("children")
        if children is not None and not isinstance(children, list):
            errors.append(f"{path}.children must be a list when present.")
        elif isinstance(children, list):
            for index, child in enumerate(children):
                walk_node(child, f"{path}.children[{index}]", errors, warnings, components, stats)

        for key in ("props", "style", "slots", "events"):
            value = node.get(key)
            if value is not None and not isinstance(value, dict):
                warnings.append(f"{path}.{key} is usually an object.")

    elif isinstance(node, list):
        stats["arrayNodes"] += 1
        for index, child in enumerate(node):
            walk_node(child, f"{path}[{index}]", errors, warnings, components, stats)
    else:
        stats["primitiveNodes"] += 1


def analyze_render_json(render_json: str | dict[str, Any] | list[Any]) -> dict[str, Any]:
    parsed, parse_errors = parse_render_json(render_json)
    if parse_errors:
        return {
            "valid": False,
            "errors": parse_errors,
            "warnings": [],
            "summary": "JSON parse failed.",
        }

    errors: list[str] = []
    warnings: list[str] = []
    components: list[str] = []
    stats = {
        "visited": 0,
        "objectNodes": 0,
        "arrayNodes": 0,
        "primitiveNodes": 0,
        "componentNodes": 0,
    }

    if not isinstance(parsed, (dict, list)):
        errors.append("Root JSON must be an object or array.")
    else:
        walk_node(parsed, "$", errors, warnings, components, stats)
        if isinstance(parsed, dict) and not is_component_node(parsed) and "children" not in parsed:
            warnings.append("Root object does not look like a render node yet; missing component/type/name/children.")

    summary = (
        f"Visited {stats['visited']} nodes, found {stats['componentNodes']} component-like nodes, "
        f"{len(errors)} errors, {len(warnings)} warnings."
    )
    return {
        "valid": not errors,
        "errors": errors,
        "warnings": warnings,
        "stats": stats,
        "components": components,
        "rootType": type(parsed).__name__,
        "summary": summary,
        "normalized": parsed,
    }
