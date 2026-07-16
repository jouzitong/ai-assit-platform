package ai.platform.aiassit.agent.runtime.tool;

import lombok.Builder;
import lombok.Value;

/** Stable worker-facing Tool result envelope. */
@Value
@Builder
public class ToolGatewayResponse {
    String toolCode;
    Integer toolVersion;
    String status;
    Object output;
    long durationMs;
}
