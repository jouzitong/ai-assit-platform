package ai.platform.aiassit.chat.api.dto;

import lombok.Data;

@Data
public class AiChatConversationDeleteRequest {

    private Long userId;

    private String sessionCode;
}
