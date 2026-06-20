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
    MYSQL(1, "MYSQL", "MySQL"),
    POSTGRESQL(2, "POSTGRESQL", "PostgreSQL"),
    CLICKHOUSE(3, "CLICKHOUSE", "ClickHouse"),
    ORACLE(4, "ORACLE", "Oracle"),
    SQL_SERVER(5, "SQL_SERVER", "SQL Server"),
    HIVE(6, "HIVE", "Hive"),
    ;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    DbDataSourceDbType(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
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
            if (String.valueOf(item.code).equals(text) || item.name.equalsIgnoreCase(normalized) || item.name().equalsIgnoreCase(normalized)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unsupported data source db type: " + value);
    }
}
