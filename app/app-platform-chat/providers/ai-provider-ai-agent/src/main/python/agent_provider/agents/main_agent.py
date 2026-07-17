from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..compiler import CompiledAgent, CompiledGraph


MAIN_AGENT_CODE = "home-assistant"


def resolve_main_agent(graph: "CompiledGraph") -> "CompiledAgent":
    """The root Agent is the only user-facing entry for one conversation run.

    The graph is created from the Python-local catalog before this method runs;
    Java supplies only the selected entry identifier and run context.
    """

    return graph.root
