package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;



@Data
public class ToolDefinition implements Serializable {

    /** 工具名称 */
    private String name;
    /** 工具功能描述 */
    private String description;
    /** 工具入参 JSON Schema */
    private Map<String, Object> inputSchema = new HashMap<>();
}
