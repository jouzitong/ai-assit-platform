package ai.platform.aiassit.db.engine.meta.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 数据源认证类型。
 */
@Getter
public enum DbDataSourceAuthType implements IEnum {
    NONE(0, "无认证"),
    BASIC(1, "用户名/密码"),
    BEARER(2, "Bearer"),
    AK_SK(3, "AK/SK"),
    API_KEY(4, "API Key"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    DbDataSourceAuthType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    @JsonCreator
    public static DbDataSourceAuthType of(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        for (DbDataSourceAuthType item : values()) {
            if (String.valueOf(item.code).equals(text) || item.name.equalsIgnoreCase(text) || item.name().equalsIgnoreCase(text)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unsupported data source auth type: " + value);
    }
}
