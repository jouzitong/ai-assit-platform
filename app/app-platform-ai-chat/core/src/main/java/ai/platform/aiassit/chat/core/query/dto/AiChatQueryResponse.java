package ai.platform.aiassit.chat.core.query.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class AiChatQueryResponse {

    private String requestId;

    private String sessionCode;

    private String roundCode;

    private String modelCode;

    private String providerCode;

    private String answer;

    private String status;

    private Integer inputTokens = 0;

    private Integer outputTokens = 0;

    private Integer totalTokens = 0;

    private String finishReason;

    private Map<String, Object> providerMeta = new HashMap<>();
}
