package ai.platform.aiassit.service.ai.spi.agent;

import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Set;

/** Immutable capability declaration used during publish and runtime selection. */
@Value
@Builder
public class AgentRuntimeCapabilities {
    AgentRuntimeType runtimeType;
    String sdkVersion;
    @Builder.Default
    Set<String> features = Collections.emptySet();

    public boolean supports(String feature) {
        return feature != null && features.contains(feature);
    }
}
