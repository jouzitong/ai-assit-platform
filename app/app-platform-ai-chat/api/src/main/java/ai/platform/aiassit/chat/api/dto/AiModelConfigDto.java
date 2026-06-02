package ai.platform.aiassit.chat.api.dto;

import lombok.Data;

@Data
public class AiModelConfigDto {

    private Long id;

    private String modelCode;

    private String modelName;

    private String providerCode;

    private String apiModel;

    private String capabilityTags;

    private Integer maxContextTokens;

    private Integer maxOutputTokens;

    private Integer temperatureEnabled;

    private Boolean enabled;

    private Integer priority;

    private String remark;
}
