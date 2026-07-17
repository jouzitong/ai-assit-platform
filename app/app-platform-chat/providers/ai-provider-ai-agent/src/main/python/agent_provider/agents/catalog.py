from __future__ import annotations

from .definitions import BUSINESS_ANALYSIS_AGENT, RENDERER_AGENT, SQL_BUILDER_AGENT, AgentDefinition


_ROLES = (
    BUSINESS_ANALYSIS_AGENT,
    RENDERER_AGENT,
    SQL_BUILDER_AGENT,
)
_ROLES_BY_CODE = {role.code: role for role in _ROLES}


def definition_for(agent_code: str | None) -> AgentDefinition | None:
    """Return the local role definition for an allowlisted specialist code."""

    return _ROLES_BY_CODE.get((agent_code or "").strip())


# Backward-compatible alias while callers migrate to the definition terminology.
role_for = definition_for
