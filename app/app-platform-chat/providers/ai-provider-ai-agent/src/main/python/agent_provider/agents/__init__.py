"""Main-Agent orchestration and on-demand specialist Agent construction."""

from .catalog import definition_for
from .main_agent import MAIN_AGENT_CODE, resolve_main_agent

__all__ = ["MAIN_AGENT_CODE", "definition_for", "resolve_main_agent"]
