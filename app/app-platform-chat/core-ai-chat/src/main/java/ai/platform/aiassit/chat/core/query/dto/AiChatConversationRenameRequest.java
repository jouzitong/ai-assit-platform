package ai.platform.aiassit.chat.core.query.dto;

import lombok.Data;

@Data
public class AiChatConversationRenameRequest {

    private Long userId;

    private String sessionCode;

    private String sessionName;
}
