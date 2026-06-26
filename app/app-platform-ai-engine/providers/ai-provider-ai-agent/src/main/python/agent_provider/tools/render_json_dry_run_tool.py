from typing import Any

from agents import function_tool

from .base import analyze_render_json


@function_tool
def render_json_dry_run_tool(render_json: str) -> dict[str, Any]:
    """Analyze render JSON without rendering it and report structure, paths, and component order."""

    analysis = analyze_render_json(render_json)
    return {
        "tool": "render_json_dry_run_tool",
        "valid": analysis["valid"],
        "summary": analysis.get("summary", ""),
        "rootType": analysis.get("rootType"),
        "stats": analysis.get("stats", {}),
        "components": analysis.get("components", []),
        "errors": analysis.get("errors", []),
        "warnings": analysis.get("warnings", []),
    }
