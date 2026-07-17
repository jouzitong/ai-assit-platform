package ai.platform.aiassit.service.ai.spi.tool;

import java.util.List;
import java.util.Map;

/** Runtime boundary for validating and executing platform-managed Python/JavaScript Tool source. */
public interface ManagedToolExecutor {

    List<String> validate(Map<String, Object> definition);

    /** Reads the SDK Tool contract declared by source code without invoking its business function. */
    default Map<String, Object> describe(Map<String, Object> definition) {
        return Map.of();
    }

    ManagedToolExecutionResult execute(ManagedToolExecutionRequest request);
}
