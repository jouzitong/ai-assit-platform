package ai.platform.aiassit.chat.history.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 内容格式。
 */
@Getter
public enum AiChatContentFormat implements IEnum {
    PLAIN_TEXT(0, "PlainText", "普通文本"),
    MARKDOWN(1, "Markdown", "Markdown格式"),
    SQL(2, "SQL", "SQL语句"),
    JSON(3, "JSON", "JSON格式"),
    TABLE(4, "Table", "表格"),
    CARD(5, "Card", "卡片"),
    ;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;


    AiChatContentFormat(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

}
