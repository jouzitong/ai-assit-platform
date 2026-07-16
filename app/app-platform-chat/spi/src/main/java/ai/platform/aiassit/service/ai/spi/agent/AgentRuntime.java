package ai.platform.aiassit.service.ai.spi.agent;

/** Executable adapter for one Agent SDK/runtime implementation. */
public interface AgentRuntime {
    AgentRuntimeCapabilities capabilities();

    AgentRunResult run(AgentDefinitionSnapshot snapshot,
                       AgentRunCommand command,
                       AgentRunObserver observer,
                       AgentCancellation cancellation);
}
