package ai.platform.aiassit.chat.api.dto;

import ai.platform.aiassit.chat.history.entity.enums.AiChatBusinessType;
import lombok.Data;

@Data
public class AiChatConversationCreateRequest {

    private Long userId;

    private String sessionName;

    private AiChatBusinessType businessType;
}
