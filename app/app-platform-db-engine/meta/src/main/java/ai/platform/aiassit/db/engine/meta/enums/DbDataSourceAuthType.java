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
    NONE(0, "NONE", "无认证"),
    BASIC(1, "BASIC", "用户名/密码"),
    BEARER(2, "BEARER", "Bearer"),
    AK_SK(3, "AK_SK", "AK/SK"),
    API_KEY(4, "API_KEY", "API Key"),
    ;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    DbDataSourceAuthType(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
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
