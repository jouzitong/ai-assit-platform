package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbVersionStatus implements IEnum {
    DRAFT(1, "DRAFT", "草稿"),
    CURRENT(2, "CURRENT", "当前生效"),
    HISTORY(3, "HISTORY", "历史版本"),
    DISCARDED(4, "DISCARDED", "已废弃"),
    ;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    AiKbVersionStatus(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
