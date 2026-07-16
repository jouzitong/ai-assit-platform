package ai.platform.aiassit.service.ai.spi.agent;

import ai.platform.aiassit.service.ai.api.dto.Usage;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Settled result of one Agent run. */
@Data
public class AgentRunResult implements Serializable {
    private String runId;
    private String finalOutput;
    private String finalAgentCode;
    private String status;
    private Usage usage = new Usage();
    private List<Map<String, Object>> artifacts = new ArrayList<>();
    private Map<String, Object> providerMeta = new LinkedHashMap<>();
}
