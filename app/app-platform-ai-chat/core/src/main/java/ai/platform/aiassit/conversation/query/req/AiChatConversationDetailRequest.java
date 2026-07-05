package ai.platform.aiassit.conversation.query.req;

import lombok.Data;

@Data
public class AiChatConversationDetailRequest {

    private Long userId;

    private String sessionCode;
}
