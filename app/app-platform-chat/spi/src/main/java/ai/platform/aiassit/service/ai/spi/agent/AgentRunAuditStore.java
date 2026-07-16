package ai.platform.aiassit.service.ai.spi.agent;

/** Persistence boundary for Agent run traceability. */
public interface AgentRunAuditStore {
    void create(AgentRunAuditRecord record);

    void update(AgentRunAuditRecord record);
}
