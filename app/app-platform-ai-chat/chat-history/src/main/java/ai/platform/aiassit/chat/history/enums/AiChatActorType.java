package ai.platform.aiassit.chat.history.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 时间线消息/过程产物的生产者类型。
 */
@Getter
public enum AiChatActorType implements IEnum {
    HUMAN(1, "Human", "用户"),
    AI(2, "AI", "AI"),
    SYSTEM(3, "System", "系统"),
    SKILL(4, "Skill", "技能"),
    ;

    private final int code;
    @JsonValue
    private final String name;
    private final String desc;

    AiChatActorType(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }


}
