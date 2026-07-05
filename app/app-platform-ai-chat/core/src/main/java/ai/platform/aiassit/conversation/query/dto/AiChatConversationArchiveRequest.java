package ai.platform.aiassit.conversation.query.dto;

import lombok.Data;

@Data
public class AiChatConversationArchiveRequest {

    private Long userId;

    private String sessionCode;

    private Boolean archived = Boolean.TRUE;
}
