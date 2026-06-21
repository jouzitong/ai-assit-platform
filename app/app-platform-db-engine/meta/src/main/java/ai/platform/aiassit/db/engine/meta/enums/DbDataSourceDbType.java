package ai.platform.aiassit.db.engine.meta.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 数据源数据库类型。
 */
@Getter
public enum DbDataSourceDbType implements IEnum {
    MYSQL(1, "MySQL"),
    POSTGRESQL(2, "PostgreSQL"),
    CLICKHOUSE(3, "ClickHouse"),
    ORACLE(4, "Oracle"),
    SQL_SERVER(5, "SQL Server"),
    HIVE(6, "Hive"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    DbDataSourceDbType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    @JsonCreator
    public static DbDataSourceDbType of(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        String normalized = text.replace('-', '_');
        for (DbDataSourceDbType item : values()) {
            if (String.valueOf(item.code).equals(text) || item.name.equalsIgnoreCase(text) || item.name().equalsIgnoreCase(normalized)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unsupported data source db type: " + value);
    }
}
