package ai.platform.aiassit.service.ai.spi.provider.dto;

import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ProviderKbSearchRequest {
    private String kbId;
    private String query;
    private Integer topK;
    private Integer page;
    private Integer pageSize;
    private Integer retrievalTopK;
    private Double similarityThreshold;
    private Double vectorSimilarityWeight;
    private String rerankId;
    private Boolean keyword;
    private Boolean highlight;
    private Boolean useKg;
    private Boolean tocEnhance;
    private List<String> documentIds = new ArrayList<>();
    private List<String> crossLanguages = new ArrayList<>();
    private Map<String, Object> metadataCondition = new LinkedHashMap<>();
    private RequestMeta meta;
}
