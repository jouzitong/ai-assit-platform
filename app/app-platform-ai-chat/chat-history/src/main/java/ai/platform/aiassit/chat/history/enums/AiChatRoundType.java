package ai.platform.aiassit.chat.history.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 会话轮次类型。
 */
@Getter
public enum AiChatRoundType implements IEnum {
    USER_QUERY(1, "用户查询"),
    CLARIFICATION(2, "澄清"),
    FOLLOW_UP(3, "跟进"),
    RETRY(4, "重试"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiChatRoundType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
