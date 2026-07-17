package ai.platform.aiassit.service.ai.spi.tool;

import lombok.Builder;
import lombok.Value;

import java.util.LinkedHashMap;
import java.util.Map;

@Value
@Builder
public class ManagedToolExecutionRequest {
    @Builder.Default
    Map<String, Object> definition = new LinkedHashMap<>();
    @Builder.Default
    Map<String, Object> arguments = new LinkedHashMap<>();
    @Builder.Default
    Map<String, Object> context = new LinkedHashMap<>();
    String executionToken;
}
