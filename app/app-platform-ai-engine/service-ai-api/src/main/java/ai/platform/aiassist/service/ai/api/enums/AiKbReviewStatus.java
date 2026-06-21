package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbReviewStatus implements IEnum {
    DRAFT(1, "草稿"),
    READY(2, "待发布"),
    REJECTED(3, "已驳回"),
    PUBLISHED(4, "已发布"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiKbReviewStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
