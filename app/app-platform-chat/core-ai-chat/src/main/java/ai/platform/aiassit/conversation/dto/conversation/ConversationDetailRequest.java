package ai.platform.aiassit.conversation.dto.conversation;

import lombok.Data;

@Data
public class ConversationDetailRequest {

    private Long userId;

    private String sessionCode;
}
