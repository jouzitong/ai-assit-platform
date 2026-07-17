from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class AgentDefinition:
    """Stable role metadata supplemented by the published Agent snapshot at runtime."""

    code: str
    version: int
    name: str
    description: str
    prompt: str
    model_ref: str
    tool_refs: tuple[str, ...]
    capabilities: tuple[str, ...]
