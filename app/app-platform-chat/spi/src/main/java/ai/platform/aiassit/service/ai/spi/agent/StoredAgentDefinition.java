package ai.platform.aiassit.service.ai.spi.agent;

import lombok.Builder;
import lombok.Value;

/** Published Agent definition returned by the control-plane store. */
@Value
@Builder
public class StoredAgentDefinition {
    String agentCode;
    Integer agentVersion;
    String name;
    String description;
    String manifestJson;
    AgentRuntimeType runtimeType;
    String sdkVersion;
    String checksum;
    String resolvedCapabilitiesJson;
    String workflowSnapshotJson;
}
