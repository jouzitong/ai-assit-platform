package ai.platform.aiassit.service.ai.spi.memory.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProviderMemoryListRequest extends ProviderMemoryRequest {
    private String memoryId;
    /** Optional provider idempotency lookup key. */
    private String externalId;
    private String agentId;
    private String sessionId;
    private String userId;
    private Integer page = 1;
    private Integer pageSize = 50;
}
