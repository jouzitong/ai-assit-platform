package ai.platform.aiassit.chat.agent.control.data.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/** Tool invocation adapter. */
@Getter
public enum ToolAdapterType implements IEnum {

    FUNCTION(1, "函数工具"),
    HTTP(2, "HTTP 工具"),
    MCP(3, "MCP 工具"),
    SCRIPT(4, "脚本工具");

    @JsonValue
    private final int code;

    private final String name;

    ToolAdapterType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
