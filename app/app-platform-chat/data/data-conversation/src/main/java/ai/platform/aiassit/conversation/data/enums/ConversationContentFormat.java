package ai.platform.aiassit.conversation.data.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 内容格式。
 */
@Getter
public enum ConversationContentFormat implements IEnum {
    PLAIN_TEXT(0, "普通文本"),
    MARKDOWN(1, "Markdown格式"),
    SQL(2, "SQL语句"),
    JSON(3, "JSON格式"),
    TABLE(4, "表格"),
    CARD(5, "卡片"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    ConversationContentFormat(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
