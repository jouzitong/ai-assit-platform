package ai.platform.aiassit.chat.core.query.dto;

import lombok.Data;

@Data
public class AiChatConversationCopyResponse {

    private String sourceSessionCode;

    private String targetSessionCode;

    private AiChatSessionVO session;
}
