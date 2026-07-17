package ai.platform.aiassit.conversation.data.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 时间线消息/过程产物的生产者类型。
 */
@Getter
public enum ConversationActorType implements IEnum {
    HUMAN(1, "用户"),
    AI(2, "AI"),
    SYSTEM(3, "系统"),
    SKILL(4, "技能"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    ConversationActorType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
