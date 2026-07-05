package ai.platform.aiassit.conversation.query.req;

import ai.platform.aiassit.chat.history.enums.AiChatBusinessType;
import lombok.Data;

@Data
public class AiChatConversationCreateRequest {

    private Long userId;

    private String sessionName;

    private AiChatBusinessType businessType;
}
