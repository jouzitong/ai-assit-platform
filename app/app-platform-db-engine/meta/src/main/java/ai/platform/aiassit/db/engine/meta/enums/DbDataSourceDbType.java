package ai.platform.aiassit.db.engine.meta.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

import java.util.Locale;

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
    MONGODB(7, "MongoDB"),
    DM8(8, "达梦 DM8"),
    KINGBASE_ES(9, "金仓 KingbaseES"),
    GAUSSDB(10, "GaussDB"),
    OCEANBASE(11, "OceanBase"),
    TDSQL(12, "TDSQL"),
    GOLDENDB(13, "GoldenDB"),
    GBASE(14, "GBase"),
    SHENTONG(15, "神通数据库"),
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
        String alias = normalized.toUpperCase(Locale.ROOT);
        switch (alias) {
            case "PG", "PGSQL", "POSTGRES" -> {
                return POSTGRESQL;
            }
            case "MONGO" -> {
                return MONGODB;
            }
            case "DM", "DAMENG" -> {
                return DM8;
            }
            case "KINGBASE", "KINGBASEES" -> {
                return KINGBASE_ES;
            }
            case "OSCAR", "SHENTONG_DB" -> {
                return SHENTONG;
            }
            default -> {
                // Continue with code, display name and enum-name matching.
            }
        }
        for (DbDataSourceDbType item : values()) {
            if (String.valueOf(item.code).equals(text) || item.name.equalsIgnoreCase(text) || item.name().equalsIgnoreCase(normalized)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unsupported data source db type: " + value);
    }
}
