package ai.platform.aiassit.execution.dto;

import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import lombok.Data;

@Data
public class AiModelTestChatResultVO {

    private Boolean success;

    private Long durationMs;

    private AiChatClientType clientType;

    private String apiModel;

    private String answer;

    private String errorMessage;
}
