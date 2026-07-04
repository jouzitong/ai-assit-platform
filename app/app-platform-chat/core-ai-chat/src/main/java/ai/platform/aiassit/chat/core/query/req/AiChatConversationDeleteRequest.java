package ai.platform.aiassit.chat.core.query.req;

import lombok.Data;

@Data
public class AiChatConversationDeleteRequest {

    private Long userId;

    private String sessionCode;
}
