package ai.platform.aiassit.conversation.dto.conversation;

import lombok.Data;

@Data
public class AiChatConversationShareResponse {

    private String sessionCode;

    private String shareCode;

    private String shareUrl;
}
