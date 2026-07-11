package ai.platform.aiassit.service.ai.api.dto;

import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import lombok.Data;

import java.util.Map;

@Data
public class AiModelConfigDTO {

    private Long id;

    private String modelCode;

    private String modelName;

    private AiChatClientType clientType;

    private String baseUrl;

    private String apiModel;

    private String capabilityTags;

    private Integer maxContextTokens;

    private Integer maxOutputTokens;

    private Integer temperatureEnabled;

    private Boolean enabled;

    private Integer priority;

    private Map<String, Object> extJson;

    private String remark;
}
