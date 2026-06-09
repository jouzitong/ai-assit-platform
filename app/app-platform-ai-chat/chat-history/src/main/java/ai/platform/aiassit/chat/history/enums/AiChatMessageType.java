package ai.platform.aiassit.chat.history.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 前端时间线消息类型。
 */
@Getter
public enum AiChatMessageType implements IEnum {
    USER_INPUT(1, "UserInput", "用户输入"),
    USER_CLARIFICATION(2, "UserClarification", "用户澄清"),
    ASSISTANT_QUESTION(3, "AssistantQuestion", "助手问题"),
    ASSISTANT_PROGRESS(4, "AssistantProgress", "助手进度"),
    ASSISTANT_SUMMARY(5, "AssistantSummary", "助手总结"),
    FINAL_ANSWER(6, "FinalAnswer", "最终答案"),
    ERROR_MESSAGE(7, "ErrorMessage", "错误信息"),
    SYSTEM_NOTICE(8, "SystemNotice", "系统通知"),;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;


    AiChatMessageType(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
