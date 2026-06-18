package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.annotation.EnumValue;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbStoreStatus implements IEnum {
    INIT(1, "INIT", "初始化"),
    ACTIVE(2, "ACTIVE", "可用"),
    SYNCING(3, "SYNCING", "同步中"),
    FAILED(4, "FAILED", "异常"),
    DISABLED(5, "DISABLED", "停用"),
    ;

    @EnumValue
    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    AiKbStoreStatus(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
