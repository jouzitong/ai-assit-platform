package ai.platform.aiassit.service.ai.spi.agent;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Frozen, secret-free definition consumed by a runtime worker.
 *
 * <p>The snapshot contains only serializable platform declarations. SDK
 * objects, callbacks, active sessions and credentials are deliberately kept
 * out of this type.</p>
 */
@Data
public class AgentDefinitionSnapshot implements Serializable {
    private String protocolVersion = "2.0";
    private String agentCode;
    private Integer agentVersion;
    private AgentRuntimeType runtimeType;
    private String sdkVersion;
    private String snapshotHash;
    private Map<String, Object> rootAgent = new LinkedHashMap<>();
    private List<Map<String, Object>> agentGraph = new ArrayList<>();
    private Map<String, Object> resolvedCapabilities = new LinkedHashMap<>();
    private Map<String, Object> workflowSnapshot = new LinkedHashMap<>();
}
