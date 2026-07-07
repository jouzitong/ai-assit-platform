import os
from typing import Any

from agents import function_tool

from .base import analyze_structured_json


CONTENT_TYPE_ENV_KEY = "AI_AGENT_VALIDATE_CONTENT_TYPE"
STRUCTURE_ENV_KEY = "AI_AGENT_VALIDATE_STRUCTURE"


def _resolve_content_type() -> str:
    return (os.getenv(CONTENT_TYPE_ENV_KEY) or "json").strip().lower()


def _resolve_structure_semantics() -> str | None:
    value = os.getenv(STRUCTURE_ENV_KEY)
    if value is None:
        return None
    normalized = value.strip()
    return normalized or None


@function_tool
def data_format_validate_tool(content: str) -> dict[str, Any]:
    """Validate content by env-driven content type and structure semantics."""

    content_type = _resolve_content_type()
    structure = _resolve_structure_semantics()

    if content_type == "json":
        analysis = analyze_structured_json(content, structure)
        result = {
            "tool": "data_format_validate_tool",
            "contentType": content_type,
            "configuredStructure": structure,
            "valid": analysis["valid"],
            "errors": analysis.get("errors", []),
            "warnings": analysis.get("warnings", []),
            "summary": analysis.get("summary", ""),
            "normalized": analysis.get("normalized"),
            "expectedStructure": analysis.get("expectedStructure"),
        }
        if not result["valid"]:
            result["retryAdvice"] = (
                "Regenerate the output as strict JSON and make it match expectedStructure exactly. "
                "Do not add markdown or explanatory text."
            )
        return result

    return {
        "tool": "data_format_validate_tool",
        "contentType": content_type,
        "configuredStructure": structure,
        "valid": True,
        "skipped": True,
        "errors": [],
        "warnings": [f"No validator is implemented for contentType={content_type} yet."],
        "summary": f"Skipped structure validation for unsupported contentType={content_type}.",
    }
