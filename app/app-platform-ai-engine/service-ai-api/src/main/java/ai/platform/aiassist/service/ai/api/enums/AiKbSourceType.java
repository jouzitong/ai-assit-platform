package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbSourceType implements IEnum {
    DB_DATA_SOURCE(1, "数据库数据源"),

    ;

    @JsonValue
    private final int code;

    private final String name;

    AiKbSourceType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
