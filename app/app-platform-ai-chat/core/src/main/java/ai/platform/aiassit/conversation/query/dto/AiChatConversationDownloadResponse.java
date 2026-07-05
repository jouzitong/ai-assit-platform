package ai.platform.aiassit.conversation.query.dto;

import lombok.Data;

@Data
public class AiChatConversationDownloadResponse {

    private String sessionCode;

    private String fileName;

    private String downloadUrl;
}
