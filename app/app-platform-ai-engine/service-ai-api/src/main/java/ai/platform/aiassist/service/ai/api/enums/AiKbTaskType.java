package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbTaskType implements IEnum {
    PUBLISH(1, "PUBLISH", "发布"),
    ROLLBACK(2, "ROLLBACK", "回滚"),
    ;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    AiKbTaskType(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
