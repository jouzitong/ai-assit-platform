package ai.platform.aiassit.execution.dto;

import lombok.Data;

@Data
public class AiModelTestChatResultVO {

    private Boolean success;

    private Long durationMs;

    private String providerCode;

    private String apiModel;

    private String answer;

    private String errorMessage;
}
