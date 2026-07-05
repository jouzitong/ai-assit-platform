package ai.platform.aiassit.conversation.dto.conversation;

import lombok.Data;

@Data
public class AiChatConversationCopyResponse {

    private String sourceSessionCode;

    private String targetSessionCode;

    private AiChatSessionVO session;
}
