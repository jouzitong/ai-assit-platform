package ai.platform.aiassit.chat.core.query.req;

import lombok.Data;

@Data
public class AiChatConversationDetailRequest {

    private Long userId;

    private String sessionCode;
}
