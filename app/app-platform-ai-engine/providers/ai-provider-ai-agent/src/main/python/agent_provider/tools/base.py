import json
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
