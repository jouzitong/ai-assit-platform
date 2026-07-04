package ai.platform.aiassit.render.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 生效状态。
 */
@Getter
public enum EffectiveStatus implements IEnum {
    DRAFT(1, "草稿"),
    PUBLISHED(2, "已发布"),
    DISABLED(3, "已停用"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    EffectiveStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
