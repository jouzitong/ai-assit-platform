package ai.platform.aiassit.conversation.dto.conversation;

import lombok.Data;

@Data
public class ConversationCopyResponse {

    private String sourceSessionCode;

    private String targetSessionCode;

    private ConversationSessionVO session;
}
