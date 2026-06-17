package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbReviewStatus implements IEnum {
    DRAFT(1, "DRAFT", "草稿"),
    READY(2, "READY", "待发布"),
    REJECTED(3, "REJECTED", "已驳回"),
    PUBLISHED(4, "PUBLISHED", "已发布"),
    ;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    AiKbReviewStatus(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
