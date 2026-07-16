package ai.platform.aiassit.agent.runtime.tool;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stable worker-to-gateway request contract. */
@Data
public class ToolGatewayRequest {
    private Map<String, Object> arguments = new LinkedHashMap<>();
    private Map<String, Object> run = new LinkedHashMap<>();
}
