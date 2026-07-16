package ai.platform.aiassit.service.ai.spi.agent;

import ai.platform.aiassit.service.ai.api.dto.ChatMessage;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One application-level Agent turn. */
@Data
public class AgentRunCommand implements Serializable {
    private String runId;
    private String requestId;
    private String traceId;
    private String sessionCode;
    private String roundCode;
    private Long userId;
    private String input;
    private List<ChatMessage> messages = new ArrayList<>();
    private Map<String, Object> context = new LinkedHashMap<>();
    private AgentModelConnection modelConnection = new AgentModelConnection();
    private Integer maxTurns;
    private Integer timeoutMs;
}
