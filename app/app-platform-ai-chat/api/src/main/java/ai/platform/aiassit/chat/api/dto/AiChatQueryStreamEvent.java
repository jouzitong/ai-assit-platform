package ai.platform.aiassit.chat.api.dto;

import lombok.Data;

@Data
public class AiChatQueryStreamEvent {

    private String eventType;

    private String requestId;

    private String sessionCode;

    private String roundCode;

    private String delta;

    private String answer;

    private String status;

    private String message;
}
