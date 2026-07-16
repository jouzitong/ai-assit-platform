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
    AGENT_CHAT(3, "智能体对话"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiChatRoundType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static AiChatRoundType fromName(String value) {
        if (!org.springframework.util.StringUtils.hasText(value)) {
            return AGENT_CHAT;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (SIMPLE_CHAT.name().equals(normalized)) {
            return SIMPLE_CHAT;
        }
        if (AGENT_CHAT.name().equals(normalized)) {
            return AGENT_CHAT;
        }
        if (QUERY_RENDER.name().equals(normalized)) {
            return QUERY_RENDER;
        }
        return AGENT_CHAT;
    }
}
