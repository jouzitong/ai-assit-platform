package ai.platform.aiassit.service.ai.api.dto;

import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import lombok.Data;

@Data
public class AiEnabledModelDTO {

    private String modelCode;

    private String modelName;

    private String apiModel;

    private AiChatClientType clientType;

    private Integer maxContextTokens;

    private Integer maxOutputTokens;

    private Integer priority;
}
