package ai.platform.aiassit.conversation.dto.conversation;

import lombok.Data;

@Data
public class AiChatConversationDeleteRequest {

    private Long userId;

    private String sessionCode;
}
