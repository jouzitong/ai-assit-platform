package ai.platform.aiassit.service.ai.spi.memory.dto;

import ai.platform.aiassit.service.ai.api.memory.enums.MemoryType;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/** Neutral, text-bearing message returned transiently by a Memory provider. */
@Data
public class MemoryMessage implements Serializable {
    private String memoryId;
    private String messageId;
    /** Provider-side idempotency locator, if the deployment exposes it. */
    private String externalId;
    private MemoryType memoryType;
    private String content;
    private Double similarity;
    private Boolean enabled;
    private String agentId;
    private String sessionId;
    private String userId;
    private String sourceId;
    private String processingStatus;
    private Instant createdAt;
}
