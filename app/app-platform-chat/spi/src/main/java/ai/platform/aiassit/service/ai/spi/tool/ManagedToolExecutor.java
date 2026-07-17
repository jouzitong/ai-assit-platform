package ai.platform.aiassit.service.ai.spi.tool;

import java.util.List;
import java.util.Map;

/** Runtime boundary for validating and executing platform-managed Python/JavaScript Tool source. */
public interface ManagedToolExecutor {

    List<String> validate(Map<String, Object> definition);

    ManagedToolExecutionResult execute(ManagedToolExecutionRequest request);
}
