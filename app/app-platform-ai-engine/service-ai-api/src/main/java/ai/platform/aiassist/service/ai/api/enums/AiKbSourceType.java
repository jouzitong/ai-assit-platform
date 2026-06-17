package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbSourceType implements IEnum {
    DB_DATA_SOURCE(1, "DB_DATA_SOURCE", "数据库数据源"),
    ;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    AiKbSourceType(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
