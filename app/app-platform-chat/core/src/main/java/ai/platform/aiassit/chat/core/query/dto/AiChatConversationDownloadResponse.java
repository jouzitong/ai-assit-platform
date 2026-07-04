package ai.platform.aiassit.chat.core.query.dto;

import lombok.Data;

@Data
public class AiChatConversationDownloadResponse {

    private String sessionCode;

    private String fileName;

    private String downloadUrl;
}
