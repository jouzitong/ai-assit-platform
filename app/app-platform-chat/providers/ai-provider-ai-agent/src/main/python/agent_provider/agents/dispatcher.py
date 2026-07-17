from __future__ import annotations

from collections.abc import Callable
from typing import Any

from ..compiler import AgentLink, CompiledAgent, CompiledGraph
from ..events import EventEmitter, emit_sdk_event


class AgentDispatcher:
    """Creates and executes only the specialist Agents approved in this snapshot."""

    def __init__(self,
                 graph: CompiledGraph,
                 emitter: EventEmitter,
                 build_agent: Callable[[str], Any],
                 compiled_for: Callable[[Any], CompiledAgent | None],
                 function_tool: Any) -> None:
        self.graph = graph
        self.emitter = emitter
        self.build_agent = build_agent
        self.compiled_for = compiled_for
        self.function_tool = function_tool

    def tools_for(self, owner: CompiledAgent) -> list[Any]:
        return [self._tool_for(link) for link in owner.agent_tools]

    def _tool_for(self, link: AgentLink) -> Any:
        target = self.graph.agents[link.target_key]
        tool_name = link.tool_name or f"ask_{_safe_identifier(target.code)}"

        async def delegate(task: str) -> str:
            # Import lazily to avoid coupling Agent factory construction to runtime package initialization.
            from ..runtime.confidence_guard import ConfidencePolicy, guard_output

            child = self.build_agent(link.target_key)
            confidence_policy = ConfidencePolicy.from_payload(self.graph.payload)
            self.emitter.event(
                "agent.delegated",
                status="RUNNING",
                message=f"Delegated task to {target.name}",
                agent=target,
                ext={"targetAgentCode": target.code, "targetAgentVersion": target.version},
            )
            from agents import Runner

            child_result = Runner.run_streamed(child, task, max_turns=self.graph.max_turns)
            async for event in child_result.stream_events():
                emit_sdk_event(
                    event,
                    self.emitter,
                    self.compiled_for,
                    self._gateway_tool_identity,
                    emit_output_deltas=not confidence_policy.requires_guard,
                )
            self.emitter.event(
                "agent.delegation.completed",
                status="SUCCESS",
                message=f"{target.name} completed delegated task",
                agent=target,
                ext={"targetAgentCode": target.code, "targetAgentVersion": target.version},
            )
            guarded_output = await guard_output(
                sdk_agent=child,
                compiled_agent=target,
                graph=self.graph,
                emitter=self.emitter,
                original_task=task,
                initial_output=child_result.final_output,
                policy=confidence_policy,
            )
            return guarded_output.text

        decorator = self.function_tool(
            name_override=tool_name,
            description_override=link.description or target.description or f"Delegate work to {target.name}.",
        )
        return decorator(delegate)

    def _gateway_tool_identity(self, sdk_name: str | None) -> dict[str, Any] | None:
        if not sdk_name:
            return None
        for descriptor in self.graph.gateway_tools.values():
            if descriptor.get("sdkName") == sdk_name:
                return {"code": descriptor.get("code"), "version": descriptor.get("version")}
        return None


def _safe_identifier(value: str) -> str:
    import re

    result = re.sub(r"[^a-zA-Z0-9_]+", "_", value).strip("_").lower()
    return result or "agent"
