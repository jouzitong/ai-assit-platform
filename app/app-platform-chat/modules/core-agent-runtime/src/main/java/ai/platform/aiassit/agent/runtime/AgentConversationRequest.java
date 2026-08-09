package ai.platform.aiassit.agent.runtime;

import ai.platform.aiassit.service.ai.api.dto.ChatMessage;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runtime-independent input assembled by the conversation domain. */
@Data
public class AgentConversationRequest {
    private String runId;
    private String requestId;
    private String traceId;
    private String sessionCode;
    private String roundCode;
    private String tenantId;
    private Long userId;
    private Long modelId;
    private String input;
    private AgentTarget target = AgentTarget.homeChat();
    private AgentArtifactDelivery artifactDelivery = AgentArtifactDelivery.STANDARD;
    private List<ChatMessage> messages = new ArrayList<>();
    private Map<String, Object> context = new LinkedHashMap<>();
}
