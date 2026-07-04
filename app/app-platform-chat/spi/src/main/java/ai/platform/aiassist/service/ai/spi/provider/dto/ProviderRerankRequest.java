package ai.platform.aiassist.service.ai.spi.provider.dto;

import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProviderRerankRequest {
    private String model;
    private String query;
    private List<String> candidates = new ArrayList<>();
    private Integer topN;
    private RequestMeta meta;
}
