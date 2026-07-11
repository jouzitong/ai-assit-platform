package ai.platform.aiassit.conversation.protocol.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ChatEventEnvelope {

    private String eventId;

    private String eventType;

    private String schemaVersion = "chat-event.v2";

    private String runId;

    private String requestId;

    private String sessionCode;

    private String roundCode;

    private String timestamp;

    private Map<String, Object> payload = new LinkedHashMap<>();
}
