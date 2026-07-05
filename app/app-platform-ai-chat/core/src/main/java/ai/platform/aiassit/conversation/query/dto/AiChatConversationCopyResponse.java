package ai.platform.aiassit.conversation.query.dto;

import lombok.Data;

@Data
public class AiChatConversationCopyResponse {

    private String sourceSessionCode;

    private String targetSessionCode;

    private AiChatSessionVO session;
}
