package ai.platform.aiassit.chat.history.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 会话轮次类型。
 */
@Getter
public enum AiChatRoundType implements IEnum {
    USER_QUERY(1, "UserQuery", "用户查询"),
    CLARIFICATION(2, "Clarification", "澄清"),
    FOLLOW_UP(3, "FollowUp", "跟进"),
    RETRY(4, "Retry", "重试"),;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    AiChatRoundType(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
