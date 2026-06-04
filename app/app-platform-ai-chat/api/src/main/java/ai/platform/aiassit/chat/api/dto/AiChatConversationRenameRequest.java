package ai.platform.aiassit.chat.api.dto;

import lombok.Data;

@Data
public class AiChatConversationRenameRequest {

    private Long userId;

    private String sessionCode;

    private String sessionName;
}
