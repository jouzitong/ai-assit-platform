package ai.platform.aiassit.chat.core.workflow.dto;

import lombok.Data;

@Data
public class AiChatQueryStreamEvent {

    private String eventType;

    private String requestId;

    private String sessionCode;

    private String sessionName;

    private String roundCode;

    private String delta;

    private String answer;

    private String status;

    private String message;
}
