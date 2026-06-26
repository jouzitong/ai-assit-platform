from typing import Any

from agents import function_tool

from .base import analyze_render_json


@function_tool
def render_json_preview_tool(render_json: str) -> dict[str, Any]:
    """Generate a human-readable preview summary for render JSON."""

    analysis = analyze_render_json(render_json)
    preview_lines: list[str] = [
        f"valid={analysis['valid']}",
        analysis.get("summary", ""),
    ]
    components = analysis.get("components", [])
    if components:
        preview_lines.append("components:")
        preview_lines.extend(components[:20])
    if analysis.get("warnings"):
        preview_lines.append("warnings:")
        preview_lines.extend(analysis["warnings"][:10])
    if analysis.get("errors"):
        preview_lines.append("errors:")
        preview_lines.extend(analysis["errors"][:10])

    return {
        "tool": "render_json_preview_tool",
        "valid": analysis["valid"],
        "previewText": "\n".join(line for line in preview_lines if line),
        "stats": analysis.get("stats", {}),
        "components": components[:20],
        "errors": analysis.get("errors", []),
        "warnings": analysis.get("warnings", []),
    }
