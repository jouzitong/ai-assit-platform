package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbBizType implements IEnum {
    DB_DATA_SOURCE(1, "数据库数据源"),
    BUSINESS_ANALYSIS_SCENE(2, "业务分析场景"),
    USER_PROFILE_SCENE(3, "用户画像/偏好场景"),
    RENDER_JSON_SCENE(4, "Render JSON 渲染场景"),
    FAQ_SCENE(5, "常见问题场景"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiKbBizType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static AiKbBizType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (AiKbBizType item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        return null;
    }

}
