package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum FinishReason implements IEnum {
    STOP(1, "正常结束"),
    LENGTH(2, "长度限制"),
    TOOL_CALL(3, "工具调用"),
    ERROR(4, "异常"),
    CONTENT_FILTER(5, "内容过滤"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    FinishReason(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
