package ai.platform.aiassit.conversation.data.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum ConversationBusinessType implements IEnum {
    GENERAL(1, "系统聊天"),
    CUSTOM(2, "用户聊天"),
    PAGE_ASSISTANT(3, "页面助手"),

    ;

    @JsonValue
    private final int code;

    private final String name;

    ConversationBusinessType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
