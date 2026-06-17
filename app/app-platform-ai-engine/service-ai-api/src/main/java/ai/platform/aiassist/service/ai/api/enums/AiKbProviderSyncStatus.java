package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbProviderSyncStatus implements IEnum {
    PENDING(1, "PENDING", "待同步"),
    RUNNING(2, "RUNNING", "同步中"),
    SUCCESS(3, "SUCCESS", "同步成功"),
    FAILED(4, "FAILED", "同步失败"),
    ;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    AiKbProviderSyncStatus(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
