package ai.platform.aiassit.chat.api.dto;

import lombok.Data;

@Data
public class AiChatConversationDetailRequest {

    private Long userId;

    private String sessionCode;
}
