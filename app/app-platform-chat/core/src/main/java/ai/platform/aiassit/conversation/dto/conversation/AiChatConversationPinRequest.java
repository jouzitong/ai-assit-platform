package ai.platform.aiassit.conversation.dto.conversation;

import lombok.Data;

@Data
public class AiChatConversationPinRequest {

    private Long userId;

    private String sessionCode;

    private Boolean pinned;
}
