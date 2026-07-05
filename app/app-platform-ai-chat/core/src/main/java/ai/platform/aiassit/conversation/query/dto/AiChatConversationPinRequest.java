package ai.platform.aiassit.conversation.query.dto;

import lombok.Data;

@Data
public class AiChatConversationPinRequest {

    private Long userId;

    private String sessionCode;

    private Boolean pinned;
}
