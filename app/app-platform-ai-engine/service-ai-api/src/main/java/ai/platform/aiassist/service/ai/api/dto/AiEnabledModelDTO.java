package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

@Data
public class AiEnabledModelDTO {

    private String apiModel;

    private String providerCode;

    private String providerName;

    private Integer maxContextTokens;

    private Integer maxOutputTokens;

    private Integer priority;
}
