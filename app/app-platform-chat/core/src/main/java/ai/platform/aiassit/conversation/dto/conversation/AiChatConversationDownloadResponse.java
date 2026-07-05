package ai.platform.aiassit.conversation.dto.conversation;

import lombok.Data;

@Data
public class AiChatConversationDownloadResponse {

    private String sessionCode;

    private String fileName;

    private String downloadUrl;
}
