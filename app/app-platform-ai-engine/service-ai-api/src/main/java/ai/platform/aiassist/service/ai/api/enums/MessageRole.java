package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum MessageRole implements IEnum {
    SYSTEM(1, "系统"),
    USER(2, "用户"),
    ASSISTANT(3, "助手"),
    TOOL(4, "工具"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    MessageRole(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
