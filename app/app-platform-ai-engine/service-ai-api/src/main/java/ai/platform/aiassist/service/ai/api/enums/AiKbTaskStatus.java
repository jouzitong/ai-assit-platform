package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbTaskStatus implements IEnum {
    PENDING(1, "PENDING", "待执行"),
    RUNNING(2, "RUNNING", "执行中"),
    SUCCESS(3, "SUCCESS", "执行成功"),
    FAILED(4, "FAILED", "执行失败"),
    CANCELED(5, "CANCELED", "已取消"),
    ;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    AiKbTaskStatus(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
