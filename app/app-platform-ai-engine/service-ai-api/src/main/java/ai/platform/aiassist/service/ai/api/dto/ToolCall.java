package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;



@Data
public class ToolCall implements Serializable {

    /** 工具调用唯一 ID */
    private String id;
    /** 工具名称 */
    private String name;
    /** 工具入参 */
    private Map<String, Object> arguments = new HashMap<>();
}
