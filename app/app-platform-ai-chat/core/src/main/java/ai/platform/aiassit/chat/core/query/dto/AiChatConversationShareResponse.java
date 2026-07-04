package ai.platform.aiassit.chat.core.query.dto;

import lombok.Data;

@Data
public class AiChatConversationShareResponse {

    private String sessionCode;

    private String shareCode;

    private String shareUrl;
}
