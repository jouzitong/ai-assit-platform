package ai.platform.aiassit.chat.history.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

import java.util.Locale;

/**
 * 会话轮次业务类型。
 */
@Getter
public enum AiChatRoundType implements IEnum {
    QUERY_RENDER(1, "用户智能问数"),
    SIMPLE_CHAT(2, "普通对话"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiChatRoundType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static AiChatRoundType fromIntentType(String intentType) {
        if (!org.springframework.util.StringUtils.hasText(intentType)) {
            return QUERY_RENDER;
        }
        String normalized = intentType.trim().toUpperCase(Locale.ROOT);
        if (SIMPLE_CHAT.name().equals(normalized)) {
            return SIMPLE_CHAT;
        }
        return QUERY_RENDER;
    }
}
