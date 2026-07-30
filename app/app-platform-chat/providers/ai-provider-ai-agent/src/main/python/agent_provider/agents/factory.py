from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass, field
from typing import Any

from ..artifacts import RunArtifactCollector
from ..compiler import CompiledAgent, CompiledGraph
from ..events import EventEmitter
from ..gateway import build_gateway_tool
from ..skills import build_skill_tool
from .dispatcher import AgentDispatcher
from .main_agent import resolve_main_agent


@dataclass
class SdkGraph:
    root: Any
    agents: dict[str, Any]
    reverse: dict[int, CompiledAgent]
    artifact_collector: RunArtifactCollector = field(default_factory=RunArtifactCollector)
    agent_builder: Callable[[str], Any] | None = field(default=None, repr=False)

    def agent_for_key(self, key: str) -> Any | None:
        if self.agent_builder is not None:
            return self.agent_builder(key)
        return self.agents.get(key)

    def compiled_for(self, sdk_agent: Any) -> CompiledAgent | None:
        if sdk_agent is None:
            return None
        direct = self.reverse.get(id(sdk_agent))
        if direct is not None:
            return direct
        name = getattr(sdk_agent, "name", None)
        return next((agent for agent in self.reverse.values() if agent.name == name), None)


class AgentFactory:
    """Builds the root eagerly and specialist Agents only when delegated to."""

    def __init__(self, graph: CompiledGraph, emitter: EventEmitter) -> None:
        self.graph = graph
        self.emitter = emitter
        self._built: dict[str, Any] = {}
        self._reverse: dict[int, CompiledAgent] = {}
        self._artifact_collector = RunArtifactCollector()

        from agents import Agent, ModelSettings, function_tool

        from ..tools import (
            build_data_preview_query_tool,
            build_render_json_validate_tool,
            data_preview_query_tool,
            data_format_validate_tool,
            build_knowledge_base_search_tool,
            knowledge_base_search_tool,
            render_json_validate_tool,
            web_search_tool,
        )

        self._agent_class = Agent
        self._model_settings_class = ModelSettings
        self._function_tool = function_tool
        self._build_data_preview_query_tool = build_data_preview_query_tool
        self._build_knowledge_base_search_tool = build_knowledge_base_search_tool
        self._build_render_json_validate_tool = build_render_json_validate_tool
        self._tool_registry = {
            "data_preview_query_tool": data_preview_query_tool,
            "data_format_validate_tool": data_format_validate_tool,
            "knowledge_base_search_tool": knowledge_base_search_tool,
            "render_json_validate_tool": render_json_validate_tool,
            "web_search_tool": web_search_tool,
        }
        self._dispatcher = AgentDispatcher(
            graph,
            emitter,
            self._build,
            self.compiled_for,
            function_tool,
            self._artifact_collector,
        )

    def build_root(self) -> SdkGraph:
        root = self._build(resolve_main_agent(self.graph).key)
        return SdkGraph(
            root=root,
            agents=self._built,
            reverse=self._reverse,
            artifact_collector=self._artifact_collector,
            agent_builder=self._build,
        )

    def _build(self, key: str) -> Any:
        existing = self._built.get(key)
        if existing is not None:
            return existing
        spec = self.graph.agents[key]
        tools = self._native_tools(spec)
        tools.extend(self._delegation_tools(spec))

        # Handoffs are SDK-level control transfers, so their targets must exist before
        # the parent Agent starts. Agent-as-tool delegation remains lazy.
        handoffs = [self._build(link.target_key) for link in spec.handoffs]
        kwargs: dict[str, Any] = {
            "name": spec.name,
            "instructions": spec.instructions,
            "model": _runtime_model(spec.model),
            "tools": tools,
            "handoffs": handoffs,
            "handoff_description": spec.description or None,
        }
        settings = _model_settings(self._model_settings_class, spec.model_settings)
        if settings is not None:
            kwargs["model_settings"] = settings
        sdk_agent = self._agent_class(**_supported_kwargs(self._agent_class, kwargs))
        self._built[key] = sdk_agent
        self._reverse[id(sdk_agent)] = spec
        return sdk_agent

    def _native_tools(self, spec: CompiledAgent) -> list[Any]:
        tools: list[Any] = []
        for name in spec.tool_names:
            if name == "data_preview_query_tool":
                tools.append(self._build_data_preview_query_tool(self.graph.payload["run"], self._function_tool))
                continue
            if name == "knowledge_base_search_tool":
                tool = self._build_knowledge_base_search_tool(self.graph.payload["run"], self._function_tool)
                if tool is not None:
                    tools.append(tool)
                continue
            if name == "render_json_validate_tool":
                tools.append(self._build_render_json_validate_tool(self.graph.payload["run"], self._function_tool))
                continue
            tools.append(
                build_gateway_tool(
                    self.graph.gateway_tools[name],
                    self.graph.payload["run"],
                    self.graph.payload.get("snapshotHash"),
                )
                if name in self.graph.gateway_tools
                else self._tool_registry[name]
            )
        if self.graph.skill_catalog and spec.skill_refs:
            tools.append(build_skill_tool(self.graph, self.emitter, self._function_tool, spec.skill_refs))
        return tools

    def _delegation_tools(self, spec: CompiledAgent) -> list[Any]:
        return self._dispatcher.tools_for(spec)

    def compiled_for(self, sdk_agent: Any) -> CompiledAgent | None:
        return SdkGraph(
            None,
            self._built,
            self._reverse,
            self._artifact_collector,
            self._build,
        ).compiled_for(sdk_agent)


def _runtime_model(value: str | None) -> str:
    import os

    configured = (os.getenv("OPENAI_MODEL") or "").strip()
    if configured:
        return configured
    model = str(value or "").strip()
    if model and "://" not in model:
        return model
    return "gpt-5.5"


def _model_settings(model_settings_class: Any, values: dict[str, Any]) -> Any | None:
    if not values:
        return None
    aliases = {
        "topP": "top_p",
        "maxTokens": "max_tokens",
        "parallelToolCalls": "parallel_tool_calls",
        "toolChoice": "tool_choice",
        "frequencyPenalty": "frequency_penalty",
        "presencePenalty": "presence_penalty",
        "promptCacheRetention": "prompt_cache_retention",
        "includeUsage": "include_usage",
        "responseInclude": "response_include",
        "topLogprobs": "top_logprobs",
        "extraQuery": "extra_query",
        "extraBody": "extra_body",
        "extraHeaders": "extra_headers",
        "extraArgs": "extra_args",
        "contextManagement": "context_management",
        "promptCacheOptions": "prompt_cache_options",
    }
    normalized = {aliases.get(key, key): value for key, value in values.items() if value is not None}
    try:
        return model_settings_class(**_supported_kwargs(model_settings_class, normalized))
    except TypeError:
        return None


def _supported_kwargs(callable_value: Any, kwargs: dict[str, Any]) -> dict[str, Any]:
    import inspect

    try:
        signature = inspect.signature(callable_value)
    except (TypeError, ValueError):
        return kwargs
    if any(parameter.kind == inspect.Parameter.VAR_KEYWORD for parameter in signature.parameters.values()):
        return kwargs
    return {key: value for key, value in kwargs.items() if key in signature.parameters and value is not None}
