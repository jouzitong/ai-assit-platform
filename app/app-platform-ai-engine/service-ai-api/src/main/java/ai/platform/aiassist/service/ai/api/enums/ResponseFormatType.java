package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum ResponseFormatType implements IEnum {
    TEXT(1, "文本"),
    JSON_SCHEMA(2, "JSON Schema"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    ResponseFormatType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
