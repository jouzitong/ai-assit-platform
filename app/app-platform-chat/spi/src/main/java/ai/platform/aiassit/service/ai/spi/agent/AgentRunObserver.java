package ai.platform.aiassit.service.ai.spi.agent;

/** Observer for normalized Agent run events. */
public interface AgentRunObserver {
    AgentRunObserver NOOP = event -> { };

    void onEvent(AgentRunEvent event);
}
