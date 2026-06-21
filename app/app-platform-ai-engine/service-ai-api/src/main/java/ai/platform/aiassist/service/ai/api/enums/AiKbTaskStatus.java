package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbTaskStatus implements IEnum {
    PENDING(1, "待执行"),
    RUNNING(2, "执行中"),
    SUCCESS(3, "执行成功"),
    FAILED(4, "执行失败"),
    CANCELED(5, "已取消"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiKbTaskStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
