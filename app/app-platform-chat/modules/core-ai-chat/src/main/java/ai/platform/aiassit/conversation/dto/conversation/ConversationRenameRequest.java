package ai.platform.aiassit.conversation.dto.conversation;

import lombok.Data;

@Data
public class ConversationRenameRequest {

    private Long userId;

    private String sessionCode;

    private String sessionName;
}
