package ai.platform.aiassit.conversation.dto.conversation;

import ai.platform.aiassit.chat.history.enums.AiChatBusinessType;
import lombok.Data;

@Data
public class AiChatConversationQueryRequest {

    private Long userId;

    private String sessionCode;

    private AiChatBusinessType businessType;
}
