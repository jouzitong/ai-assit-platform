package ai.platform.aiassit.chat.api.dto;

import lombok.Data;

@Data
public class AiProviderConfigDto {

    private Long id;

    private String providerCode;

    private String providerName;

    private String baseUrl;

    private Integer connectTimeoutMs;

    private Integer readTimeoutMs;

    private Boolean enabled;

    private String remark;
}
