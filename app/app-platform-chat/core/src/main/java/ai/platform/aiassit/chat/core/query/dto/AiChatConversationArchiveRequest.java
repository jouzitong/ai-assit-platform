package ai.platform.aiassit.chat.core.query.dto;

import lombok.Data;

@Data
public class AiChatConversationArchiveRequest {

    private Long userId;

    private String sessionCode;

    private Boolean archived = Boolean.TRUE;
}
