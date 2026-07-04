package ai.platform.aiassit.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbChangeType implements IEnum {
    CREATE(1, "新增"),
    UPDATE(2, "更新"),
    DELETE(3, "删除"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiKbChangeType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
