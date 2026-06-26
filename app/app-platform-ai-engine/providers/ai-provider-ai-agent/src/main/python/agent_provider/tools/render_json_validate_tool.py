from typing import Any

from agents import function_tool

from .base import analyze_render_json


@function_tool
def render_json_validate_tool(render_json: str) -> dict[str, Any]:
    """Validate render JSON syntax and basic render-node structure."""

    analysis = analyze_render_json(render_json)
    return {
        "tool": "render_json_validate_tool",
        "valid": analysis["valid"],
        "errors": analysis.get("errors", []),
        "warnings": analysis.get("warnings", []),
        "stats": analysis.get("stats", {}),
        "summary": analysis.get("summary", ""),
    }
