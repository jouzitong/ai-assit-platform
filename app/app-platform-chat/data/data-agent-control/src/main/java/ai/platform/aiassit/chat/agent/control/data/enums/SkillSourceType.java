package ai.platform.aiassit.chat.agent.control.data.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/** Skill authoring source. */
@Getter
public enum SkillSourceType implements IEnum {

    FORM(1, "表单"),
    ZIP(2, "ZIP 包");

    @JsonValue
    private final int code;

    private final String name;

    SkillSourceType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
