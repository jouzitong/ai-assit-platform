package ai.platform.aiassit.chat.core.query.dto;

import ai.platform.aiassit.chat.history.enums.AiChatBusinessType;
import lombok.Data;

@Data
public class AiChatConversationQueryRequest {

    private Long userId;

    private String sessionCode;

    private AiChatBusinessType businessType;
}
