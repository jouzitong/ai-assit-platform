from __future__ import annotations

from typing import Any

from .definitions import (
    BUSINESS_ANALYSIS_AGENT,
    HOME_AGENT,
    RENDERER_AGENT,
    SETTINGS_AGENT,
    SQL_BUILDER_AGENT,
    AgentDefinition,
)


_ROLES = (
    HOME_AGENT,
    SETTINGS_AGENT,
    BUSINESS_ANALYSIS_AGENT,
    RENDERER_AGENT,
    SQL_BUILDER_AGENT,
)
_ROLES_BY_CODE = {role.code: role for role in _ROLES}


def definition_for(agent_code: str | None) -> AgentDefinition | None:
    """Return an allowlisted Agent definition owned by this Python package."""

    return _ROLES_BY_CODE.get((agent_code or "").strip())


# Backward-compatible alias while callers migrate to the definition terminology.
role_for = definition_for


def local_agent_documents(entry: str | None) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    """Build the complete runnable graph from Python-local definitions.

    The entry is a selector supplied by Java, not an Agent manifest. An unknown
    value fails closed instead of falling back to Java configuration.
    """

    selected = (entry or "HOME_CHAT").strip()
    root_code = {
        "": "home-assistant",
        "HOME_CHAT": "home-assistant",
        "SETTINGS_ASSISTANT": "settings-assistant",
    }.get(selected, selected)
    root = definition_for(root_code)
    if root is None:
        raise ValueError(f"Unknown Python Agent entry: {selected}")

    definitions = [root]
    if root.code == HOME_AGENT.code:
        definitions.extend(role for role in _ROLES if role.code != HOME_AGENT.code)
    documents = [_document(definition, is_root=definition.code == root.code) for definition in definitions]
    return documents[0], documents


def _document(definition: AgentDefinition, *, is_root: bool) -> dict[str, Any]:
    collaboration = {
        "agentTools": [
            {
                "targetAgentRef": f"agent://{delegation.target_code}/v1",
                "toolName": delegation.tool_name,
                "description": delegation.description,
            }
            for delegation in definition.agent_tools
        ],
        "handoffs": [],
    }
    spec: dict[str, Any] = {
        "instructions": {"type": "inline", "text": definition.prompt},
        "model": {"ref": definition.model_ref},
        "toolRefs": [{"ref": name, "required": True} for name in definition.tool_refs],
        "skillRefs": [{"ref": name, "required": True} for name in definition.skill_refs],
        "collaboration": collaboration,
    }
    if is_root:
        spec["runtimeDefaults"] = {"maxTurns": 12, "maxAgentDepth": 4}
    return {
        "metadata": {
            "code": definition.code,
            "version": definition.version,
            "name": definition.name,
            "description": definition.description,
        },
        "spec": spec,
    }
