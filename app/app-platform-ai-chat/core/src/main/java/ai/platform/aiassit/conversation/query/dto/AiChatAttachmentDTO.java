package ai.platform.aiassit.conversation.query.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class AiChatAttachmentDTO {

    private String fileId;

    private String fileName;

    private String fileUrl;

    private String mediaType;

    private Map<String, Object> ext = new HashMap<>();
}
