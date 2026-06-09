package ai.platform.aiassit.chat.history.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiChatBusinessType implements IEnum {
    GENERAL(1, "General", "系统聊天"),
    CUSTOM(2, "Custom", "用户聊天"),
    ;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    AiChatBusinessType(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

}
