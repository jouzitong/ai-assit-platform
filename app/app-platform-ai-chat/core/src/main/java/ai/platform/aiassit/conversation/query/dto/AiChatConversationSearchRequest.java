package ai.platform.aiassit.conversation.query.dto;

import lombok.Data;

@Data
public class AiChatConversationSearchRequest {

    private Long userId;

    private String keyword;

    private Integer page = 1;
}
