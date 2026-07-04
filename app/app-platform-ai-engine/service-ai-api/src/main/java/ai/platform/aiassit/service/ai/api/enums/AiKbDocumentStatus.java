package ai.platform.aiassit.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbDocumentStatus implements IEnum {
    ACTIVE(1, "启用"),
    DISABLED(2, "停用"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiKbDocumentStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
