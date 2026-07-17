from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..compiler import CompiledAgent, CompiledGraph


MAIN_AGENT_CODE = "home-assistant"


def resolve_main_agent(graph: "CompiledGraph") -> "CompiledAgent":
    """The root Agent is the only user-facing entry for one conversation run.

    Its published snapshot remains authoritative, so non-home entries can use the
    same orchestration runtime without hard-coding a separate Python root.
    """

    return graph.root
