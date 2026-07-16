package ai.platform.aiassit.service.ai.spi.agent;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Language-neutral event emitted by Python and TypeScript runtimes. */
@Data
public class AgentRunEvent implements Serializable {
    private String eventType;
    private String runId;
    private String requestId;
    private String traceId;
    private String sessionCode;
    private String roundCode;
    private String agentCode;
    private Integer agentVersion;
    private String agentName;
    private String status;
    private String delta;
    private String message;
    private Instant timestamp = Instant.now();
    private Map<String, Object> ext = new LinkedHashMap<>();
}
