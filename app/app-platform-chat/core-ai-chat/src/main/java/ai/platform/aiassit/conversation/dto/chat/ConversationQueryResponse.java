package ai.platform.aiassit.conversation.dto.chat;

import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ConversationQueryResponse {

    private String requestId;

    private String sessionCode;

    private String roundCode;

    private String modelCode;

    private AiChatClientType clientType;

    private String answer;

    private String status;

    private Integer inputTokens = 0;

    private Integer outputTokens = 0;

    private Integer totalTokens = 0;

    private String finishReason;

    private Map<String, Object> providerMeta = new HashMap<>();
}
