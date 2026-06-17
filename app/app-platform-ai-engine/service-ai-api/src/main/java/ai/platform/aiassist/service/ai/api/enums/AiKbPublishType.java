package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbPublishType implements IEnum {
    MANUAL(1, "MANUAL", "人工发布"),
    ROLLBACK(2, "ROLLBACK", "版本回滚"),
    ;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    AiKbPublishType(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
