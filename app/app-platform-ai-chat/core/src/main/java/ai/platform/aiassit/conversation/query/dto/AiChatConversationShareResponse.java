package ai.platform.aiassit.conversation.query.dto;

import lombok.Data;

@Data
public class AiChatConversationShareResponse {

    private String sessionCode;

    private String shareCode;

    private String shareUrl;
}
