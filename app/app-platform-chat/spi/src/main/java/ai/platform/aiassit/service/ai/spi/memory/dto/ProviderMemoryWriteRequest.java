package ai.platform.aiassit.service.ai.spi.memory.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProviderMemoryWriteRequest extends ProviderMemoryRequest {
    private List<String> memoryIds = new ArrayList<>();
    /** Provider-side idempotency locator; never contains message content. */
    private String externalId;
    private String agentId;
    private String sessionId;
    private String userId;
    private String userInput;
    private String agentResponse;
}
