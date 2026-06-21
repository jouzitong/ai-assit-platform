package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbPublishType implements IEnum {
    MANUAL(1, "人工发布"),
    ROLLBACK(2, "版本回滚"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiKbPublishType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
