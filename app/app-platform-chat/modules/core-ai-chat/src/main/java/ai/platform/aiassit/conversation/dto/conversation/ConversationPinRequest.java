package ai.platform.aiassit.conversation.dto.conversation;

import lombok.Data;

@Data
public class ConversationPinRequest {

    private Long userId;

    private String sessionCode;

    private Boolean pinned;
}
