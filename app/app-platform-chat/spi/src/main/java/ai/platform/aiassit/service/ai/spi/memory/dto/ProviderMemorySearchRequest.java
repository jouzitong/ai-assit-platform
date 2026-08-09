package ai.platform.aiassit.service.ai.spi.memory.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProviderMemorySearchRequest extends ProviderMemoryRequest {
    private String query;
    private List<String> memoryIds = new ArrayList<>();
    private String agentId;
    private String sessionId;
    private String userId;
    private Double similarityThreshold;
    private Double keywordsSimilarityWeight;
    private Integer topN;
}
