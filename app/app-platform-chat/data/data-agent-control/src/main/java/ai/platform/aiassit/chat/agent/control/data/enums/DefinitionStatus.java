package ai.platform.aiassit.chat.agent.control.data.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * Immutable control-plane definition lifecycle.
 */
@Getter
public enum DefinitionStatus implements IEnum {

    DRAFT(1, "草稿"),
    VALIDATED(2, "已校验"),
    PUBLISHED(3, "已发布"),
    ARCHIVED(4, "已归档");

    @JsonValue
    private final int code;

    private final String name;

    DefinitionStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
