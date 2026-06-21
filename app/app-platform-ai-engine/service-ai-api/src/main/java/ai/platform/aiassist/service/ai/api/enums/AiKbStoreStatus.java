package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbStoreStatus implements IEnum {
    INIT(1, "初始化"),
    ACTIVE(2, "可用"),
    SYNCING(3, "同步中"),
    FAILED(4, "异常"),
    DISABLED(5, "停用"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiKbStoreStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
