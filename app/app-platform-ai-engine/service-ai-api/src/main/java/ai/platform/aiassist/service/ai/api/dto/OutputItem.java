package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import ai.platform.aiassist.service.ai.api.enums.OutputType;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;



@Data
public class OutputItem implements Serializable {

    /** 输出项类型（文本/工具调用/JSON） */
    private OutputType type;
    /** 文本内容（type=TEXT 时使用） */
    private String text;
    /** 工具调用信息（type=TOOL_CALL 时使用） */
    private ToolCall toolCall;
    /** JSON 结构输出（type=JSON 时使用） */
    private Map<String, Object> json = new HashMap<>();
}
