package ai.platform.aiassit.conversation.dto.conversation;

import lombok.Data;

@Data
public class ConversationArchiveRequest {

    private Long userId;

    private String sessionCode;

    private Boolean archived = Boolean.TRUE;
}
