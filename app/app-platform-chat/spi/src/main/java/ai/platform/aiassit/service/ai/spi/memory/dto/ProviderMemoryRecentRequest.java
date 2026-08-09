package ai.platform.aiassit.service.ai.spi.memory.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProviderMemoryRecentRequest extends ProviderMemoryRequest {
    private List<String> memoryIds = new ArrayList<>();
    private String agentId;
    private String sessionId;
    private Integer limit = 10;
}
