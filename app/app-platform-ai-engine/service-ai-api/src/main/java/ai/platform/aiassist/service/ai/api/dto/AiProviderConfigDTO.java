package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

@Data
public class AiProviderConfigDTO {

    private Long id;

    private String providerCode;

    private String providerName;

    private String baseUrl;

    private Integer connectTimeoutMs;

    private Integer readTimeoutMs;

    private Boolean enabled;

    private String remark;
}
