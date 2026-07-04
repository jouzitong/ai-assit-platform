package ai.platform.aiassit.chat.core.query.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class AiChatToolDTO {

    private String toolCode;

    private String toolName;

    private Map<String, Object> ext = new HashMap<>();
}
