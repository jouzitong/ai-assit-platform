package ai.platform.aiassit.conversation.dto.conversation;

import ai.platform.aiassit.chat.history.enums.AiChatBusinessType;
import lombok.Data;

@Data
public class ConversationCreateRequest {

    private Long userId;

    private String sessionName;

    private AiChatBusinessType businessType;
}
