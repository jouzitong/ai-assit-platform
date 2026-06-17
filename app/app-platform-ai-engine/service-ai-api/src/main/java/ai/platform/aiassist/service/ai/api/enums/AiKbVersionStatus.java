package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbVersionStatus implements IEnum {
    DRAFT(1, "DRAFT", "草稿"),
    CONFIRMED(2, "CONFIRMED", "已确认"),
    PUBLISHING(3, "PUBLISHING", "发布中"),
    PUBLISHED(4, "PUBLISHED", "已发布"),
    FAILED(5, "FAILED", "发布失败"),
    ROLLED_BACK(6, "ROLLED_BACK", "已回滚"),
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
