from typing import Any

from agents import function_tool

from .render_validation import validate_render_document


def validate_render_json_for_run(run: dict[str, Any], render_json: str) -> dict[str, Any]:
    analysis = validate_render_document(render_json)
    return {
        "tool": "render_json_validate_tool",
        **analysis,
    }


def build_render_json_validate_tool(run: dict[str, Any], function_tool_factory: Any) -> Any:
    """Build the deterministic Render JSON structure and security validator."""

    def validate_render_json(render_json: str) -> dict[str, Any]:
        """Validate a complete RenderDocument, data binding, and security rules."""

        return validate_render_json_for_run(run, render_json)

    decorator = function_tool_factory(
        name_override="render_json_validate_tool",
        description_override=(
            "Deterministically validate a complete render-json document against protocol 1.0, datasource rules, "
            "declarative structure, and security constraints. Component authoring contracts come from the "
            "render-json-authoring skill."
        ),
    )
    return decorator(validate_render_json)


@function_tool
def render_json_validate_tool(render_json: str) -> dict[str, Any]:
    """Validate a complete RenderDocument against structural and security rules."""

    return validate_render_json_for_run({}, render_json)
