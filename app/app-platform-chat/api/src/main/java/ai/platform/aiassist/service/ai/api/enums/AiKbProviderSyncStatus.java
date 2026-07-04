package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbProviderSyncStatus implements IEnum {
    PENDING(1, "待同步"),
    RUNNING(2, "同步中"),
    SUCCESS(3, "同步成功"),
    FAILED(4, "同步失败"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiKbProviderSyncStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
