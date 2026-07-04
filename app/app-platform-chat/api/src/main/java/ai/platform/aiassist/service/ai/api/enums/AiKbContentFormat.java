package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbContentFormat implements IEnum {
    MARKDOWN(1, "Markdown"),
    TEXT(2, "纯文本"),
    JSON(3, "结构化 JSON"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiKbContentFormat(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
