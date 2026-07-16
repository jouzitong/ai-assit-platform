package ai.platform.aiassit.service.ai.spi.agent;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/** Immutable audit projection for one Agent run lifecycle update. */
@Value
@Builder(toBuilder = true)
public class AgentRunAuditRecord {
    String runId;
    String sessionCode;
    String roundCode;
    String rootAgentCode;
    Integer rootAgentVersion;
    String workflowCode;
    Integer workflowVersion;
    AgentRuntimeType runtimeType;
    String sdkVersion;
    String snapshotHash;
    String traceId;
    String status;
    Instant startedAt;
    Instant finishedAt;
    String usageJson;
    String errorSummary;
}
