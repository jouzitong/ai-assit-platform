package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbContentFormat implements IEnum {
    MARKDOWN(1, "MARKDOWN", "Markdown"),
    TEXT(2, "TEXT", "纯文本"),
    JSON(3, "JSON", "结构化 JSON"),
    ;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    AiKbContentFormat(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
