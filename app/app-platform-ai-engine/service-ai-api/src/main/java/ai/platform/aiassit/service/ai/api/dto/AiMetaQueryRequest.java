package ai.platform.aiassit.service.ai.api.dto;

import lombok.Data;

@Data
public class AiMetaQueryRequest {

    private String providerCode;

    private String modelCode;

    private Boolean enabled;
}
