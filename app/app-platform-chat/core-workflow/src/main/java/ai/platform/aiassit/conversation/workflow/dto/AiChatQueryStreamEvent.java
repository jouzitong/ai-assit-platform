package ai.platform.aiassit.conversation.workflow.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AiChatQueryStreamEvent {

    private String eventType;

    private String source;

    private String phase;

    private String requestId;

    private String sessionCode;

    private String sessionName;

    private String roundCode;

    private String delta;

    private String answer;

    private String status;

    private String message;

    private Map<String, Object> ext = new LinkedHashMap<>();
}
