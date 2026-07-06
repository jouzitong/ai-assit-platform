package ai.platform.aiassit.conversation.dto.conversation;

import lombok.Data;

@Data
public class ConversationSimpleRequest {

    private Long userId;

    private String sessionCode;
}
