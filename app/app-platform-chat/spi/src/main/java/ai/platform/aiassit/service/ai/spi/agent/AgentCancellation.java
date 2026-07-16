package ai.platform.aiassit.service.ai.spi.agent;

/** Cancellation signal supplied by the conversation runtime. */
@FunctionalInterface
public interface AgentCancellation {
    AgentCancellation NONE = () -> false;

    boolean isCancellationRequested();
}
