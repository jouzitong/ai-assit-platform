package ai.platform.aiassit.agent.runtime;

import ai.platform.aiassit.service.ai.spi.agent.AgentCancellation;
import ai.platform.aiassit.service.ai.spi.agent.AgentEntrySummary;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunObserver;

import java.util.List;

/** Application-facing Agent run facade. */
public interface AgentConversationRunner {
    AgentConversationOutcome run(AgentConversationRequest request,
                                 AgentRunObserver observer,
                                 AgentCancellation cancellation);

    List<AgentEntrySummary> availableAgents(String entryCode);
}
