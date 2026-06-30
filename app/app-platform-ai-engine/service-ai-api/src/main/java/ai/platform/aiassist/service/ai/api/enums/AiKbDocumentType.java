package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbDocumentType implements IEnum {
    DB_TABLE(1, "数据库表", AiKbBizType.DB_DATA_SOURCE),
    BUSINESS_DOCUMENT(2, "常见业务", AiKbBizType.BUSINESS_ANALYSIS_SCENE),
    USER_PROFILE_DOCUMENT(3, "用户画像/偏好", AiKbBizType.USER_PROFILE_SCENE),
    RENDER_DOCUMENT(4, "渲染场景", AiKbBizType.RENDER_JSON_SCENE),
    FAQ_DOCUMENT(5, "常见问题", AiKbBizType.FAQ_SCENE),
    ;

    @JsonValue
    private final int code;

    private final String name;

    /** 该文档类型默认归属的业务类型。 */
    private final AiKbBizType bizType;

    AiKbDocumentType(int code, String name, AiKbBizType bizType) {
        this.code = code;
        this.name = name;
        this.bizType = bizType;
    }
}
