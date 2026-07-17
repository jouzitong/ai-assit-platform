package ai.platform.aiassit.conversation.data.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 前端展示层级。
 */
@Getter
public enum ConversationDisplayLevel implements IEnum {
    VISIBLE(0, "可见"),
    COLLAPSIBLE(1, "可折叠"),
    HIDDEN(2, "隐藏"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    ConversationDisplayLevel(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
