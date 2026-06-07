package ai.platform.aiassist.service.ai.spi.provider.dto;

import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import lombok.Data;

@Data
public class ProviderKbSearchRequest {
    private String kbId;
    private String query;
    private Integer topK;
    private RequestMeta meta;
}
