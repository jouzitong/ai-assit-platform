package ai.platform.aiassit.chat.history.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiChatBusinessType implements IEnum {
    GENERAL(1, "系统聊天"),
    CUSTOM(2, "用户聊天"),

    ;

    @JsonValue
    private final int code;

    private final String name;

    AiChatBusinessType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
