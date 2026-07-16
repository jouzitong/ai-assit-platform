package ai.platform.aiassit.service.ai.spi.agent;

import java.util.List;
import java.util.Optional;

/** Read-only runtime projection of published control-plane definitions. */
public interface AgentDefinitionStore {
    Optional<StoredAgentDefinition> resolve(String agentCode, Integer version);

    Optional<StoredAgentDefinition> resolveEntry(String entryCode);

    List<AgentEntrySummary> listAvailable(String entryCode);
}
