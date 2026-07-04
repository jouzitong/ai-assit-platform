package ai.platform.aiassit.chat.history.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 前端时间线消息类型。
 */
@Getter
public enum AiChatMessageType implements IEnum {
    USER_INPUT(1, "用户输入"),
    USER_CLARIFICATION(2, "用户澄清"),
    ASSISTANT_QUESTION(3, "助手问题"),
    ASSISTANT_PROGRESS(4, "助手进度"),
    ASSISTANT_SUMMARY(5, "助手总结"),
    FINAL_ANSWER(6, "最终答案"),
    ERROR_MESSAGE(7, "错误信息"),
    SYSTEM_NOTICE(8, "系统通知"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiChatMessageType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
