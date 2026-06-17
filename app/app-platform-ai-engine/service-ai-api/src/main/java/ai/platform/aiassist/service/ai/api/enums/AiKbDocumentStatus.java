package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbDocumentStatus implements IEnum {
    ACTIVE(1, "ACTIVE", "启用"),
    DISABLED(2, "DISABLED", "停用"),
    ;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    AiKbDocumentStatus(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
