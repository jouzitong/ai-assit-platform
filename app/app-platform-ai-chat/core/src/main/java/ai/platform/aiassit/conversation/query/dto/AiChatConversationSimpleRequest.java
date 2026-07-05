package ai.platform.aiassit.conversation.query.dto;

import lombok.Data;

@Data
public class AiChatConversationSimpleRequest {

    private Long userId;

    private String sessionCode;
}
