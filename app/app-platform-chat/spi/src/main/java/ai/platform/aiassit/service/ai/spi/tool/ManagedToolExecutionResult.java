package ai.platform.aiassit.service.ai.spi.tool;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ManagedToolExecutionResult {
    Object output;
    String stdout;
    String stderr;
    long durationMs;
}
