package ai.platform.aiassit.chat.history.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 前端展示层级。
 */
@Getter
public enum AiChatDisplayLevel implements IEnum {
    VISIBLE(0, "Visible", "可见"),
    COLLAPSIBLE(1, "Collapsible", "可折叠"),
    HIDDEN(2, "Hidden", "隐藏"),;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    AiChatDisplayLevel(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

}
