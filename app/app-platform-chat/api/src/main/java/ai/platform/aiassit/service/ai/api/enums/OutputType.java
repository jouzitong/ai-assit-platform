package ai.platform.aiassit.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum OutputType implements IEnum {
    TEXT(1, "文本"),
    TOOL_CALL(2, "工具调用"),
    JSON(3, "JSON"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    OutputType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
