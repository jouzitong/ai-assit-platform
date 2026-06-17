package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbChangeType implements IEnum {
    CREATE(1, "CREATE", "新增"),
    UPDATE(2, "UPDATE", "更新"),
    DELETE(3, "DELETE", "删除"),
    ;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    AiKbChangeType(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
