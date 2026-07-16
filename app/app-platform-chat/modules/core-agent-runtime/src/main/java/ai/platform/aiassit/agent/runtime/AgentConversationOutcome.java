package ai.platform.aiassit.agent.runtime;

import ai.platform.aiassit.service.ai.api.dto.Usage;
import ai.platform.aiassit.service.ai.spi.agent.AgentRuntimeType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Traceable result returned to the conversation domain. */
@Data
public class AgentConversationOutcome {
    private String runId;
    private String answer;
    private String rootAgentCode;
    private Integer rootAgentVersion;
    private AgentRuntimeType runtimeType;
    private String sdkVersion;
    private String snapshotHash;
    private String modelCode;
    private String actualModel;
    private String status;
    private Usage usage = new Usage();
    private List<Map<String, Object>> artifacts = new ArrayList<>();
}
