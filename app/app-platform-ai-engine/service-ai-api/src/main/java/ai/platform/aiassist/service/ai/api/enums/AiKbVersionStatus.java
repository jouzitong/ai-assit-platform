package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbVersionStatus implements IEnum {
    DRAFT(1, "草稿"),
    CURRENT(2, "当前生效"),
    HISTORY(3, "历史版本"),
    DISCARDED(4, "已废弃"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiKbVersionStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
