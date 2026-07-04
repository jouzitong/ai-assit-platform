package ai.platform.aiassit.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbTaskType implements IEnum {
    PUBLISH(1, "发布"),
    ROLLBACK(2, "回滚"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiKbTaskType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
