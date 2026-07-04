package ai.platform.aiassit.chat.core.query.dto;

import lombok.Data;

@Data
public class AiChatConversationSimpleRequest {

    private Long userId;

    private String sessionCode;
}
