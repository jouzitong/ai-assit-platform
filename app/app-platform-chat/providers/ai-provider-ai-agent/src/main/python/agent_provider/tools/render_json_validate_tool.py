from typing import Any

from agents import function_tool

from .render_component_catalog_tool import fetch_component_catalog
from .render_validation import validate_render_document


def validate_render_json_for_run(run: dict[str, Any], render_json: str) -> dict[str, Any]:
    analysis = validate_render_document(
        render_json,
        catalog_loader=lambda keys: fetch_component_catalog(
            run,
            component_keys=keys,
            limit=len(keys) or 1,
            include_documentation=True,
        ),
    )
    return {
        "tool": "render_json_validate_tool",
        **analysis,
    }


def build_render_json_validate_tool(run: dict[str, Any], function_tool_factory: Any) -> Any:
    """Build a run-scoped validator that always checks the live component catalog."""

    def validate_render_json(render_json: str) -> dict[str, Any]:
        """Validate a complete RenderDocument, component contract, data binding, and security rules."""

        return validate_render_json_for_run(run, render_json)

    decorator = function_tool_factory(
        name_override="render_json_validate_tool",
        description_override=(
            "Deterministically validate a complete render-json document against protocol 1.0, the live published component "
            "catalog, prop/event contracts, datasource rules, and security constraints."
        ),
    )
    return decorator(validate_render_json)


@function_tool
def render_json_validate_tool(render_json: str) -> dict[str, Any]:
    """Validate a complete RenderDocument against live component and security rules."""

    return validate_render_json_for_run({}, render_json)
