from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class AgentDelegation:
    """A main-Agent tool that may lazily run another local Agent."""

    target_code: str
    tool_name: str
    description: str


@dataclass(frozen=True)
class AgentDefinition:
    """Complete local Agent contract; Java never supplements this definition."""

    code: str
    version: int
    name: str
    description: str
    prompt: str
    model_ref: str
    tool_refs: tuple[str, ...]
    capabilities: tuple[str, ...]
    skill_refs: tuple[str, ...] = ()
    agent_tools: tuple[AgentDelegation, ...] = ()
