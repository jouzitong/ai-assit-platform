package ai.platform.aiassit.conversation.query.req;

import lombok.Data;

@Data
public class AiChatConversationDeleteRequest {

    private Long userId;

    private String sessionCode;
}
