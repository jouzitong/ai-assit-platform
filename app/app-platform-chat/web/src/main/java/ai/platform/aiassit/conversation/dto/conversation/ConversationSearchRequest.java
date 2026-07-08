package ai.platform.aiassit.conversation.dto.conversation;

import lombok.Data;

@Data
public class ConversationSearchRequest {

    private Long userId;

    private String keyword;

    private Integer page = 1;
}
