package ai.platform.aiassit.db.engine.executor.jdbc.support;

import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbTableColumnDefinition;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 关系库 DDL 的安全拼装与兼容模式差异。 */
public final class JdbcDdlSupport {

    private static final Pattern IDENTIFIER = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_$#]*");
    private static final Pattern NUMERIC_LITERAL = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Set<String> DATA_TYPES = Set.of(
            "BIGINT", "BIGSERIAL", "BINARY", "BIT", "BLOB", "BOOL", "BOOLEAN", "BYTEA",
            "CHAR", "CLOB", "DATE", "DATETIME", "DECIMAL", "DOUBLE", "DOUBLE PRECISION",
            "FLOAT", "GEOMETRY", "INT", "INTEGER", "INTERVAL DAY TO SECOND", "JSON", "JSONB",
            "LONG RAW", "LONGVARCHAR", "MEDIUMINT", "MONEY", "NCHAR", "NCLOB", "NUMBER",
            "NUMERIC", "NVARCHAR", "NVARCHAR2", "RAW", "REAL", "SERIAL", "SMALLINT", "TEXT",
            "TIME", "TIMESTAMP", "TIMESTAMP WITH LOCAL TIME ZONE", "TIMESTAMP WITH TIME ZONE",
            "TIMESTAMP WITHOUT TIME ZONE", "TINYINT", "UUID", "VARBINARY", "VARCHAR", "VARCHAR2", "XML"
    );

    private final JdbcDatabaseProfile.DdlFamily family;

    public JdbcDdlSupport(JdbcDatabaseProfile.DdlFamily family) {
        this.family = family;
    }

    public String quoteIdentifier(String identifier) throws DbAccessException {
        String value = requireIdentifier(identifier, "标识符");
        String quote = family == JdbcDatabaseProfile.DdlFamily.MYSQL ? "`" : "\"";
        return quote + value.replace(quote, quote + quote) + quote;
    }

    public String quoteResource(String resource) throws DbAccessException {
        if (!StringUtils.hasText(resource)) {
            throw new DbAccessException("资源标识不能为空");
        }
        String[] parts = resource.trim().split("\\.", -1);
        if (parts.length < 1 || parts.length > 3) {
            throw new DbAccessException("资源标识最多允许 catalog.schema.table 三级");
        }
        List<String> quoted = new ArrayList<>();
        for (String part : parts) {
            quoted.add(quoteIdentifier(part));
        }
        return String.join(".", quoted);
    }

    public String qualifiedTable(String namespace, String tableName) throws DbAccessException {
        if (StringUtils.hasText(namespace)) {
            return quoteIdentifier(namespace) + "." + quoteIdentifier(tableName);
        }
        return quoteIdentifier(tableName);
    }

    public String createTable(
            String namespace,
            String tableName,
            String tableComment,
            List<DbTableColumnDefinition> columns
    ) throws DbAccessException {
        if (columns == null || columns.isEmpty()) {
            throw new DbAccessException("创建表时字段列表不能为空");
        }
        List<String> definitions = new ArrayList<>();
        for (DbTableColumnDefinition column : columns) {
            definitions.add(columnDefinition(column, true));
        }
        List<DbTableColumnDefinition> primaryColumns = columns.stream()
                .filter(column -> column != null && Boolean.TRUE.equals(column.getPrimaryKey()))
                .toList();
        if (!primaryColumns.isEmpty()) {
            definitions.add("PRIMARY KEY (" + primaryColumns.stream()
                    .map(column -> quoteIdentifierUnchecked(column.getColumnName()))
                    .collect(Collectors.joining(", ")) + ")");
        }
        String sql = "CREATE TABLE " + qualifiedTable(namespace, tableName)
                + " (" + String.join(", ", definitions) + ")";
        if (family == JdbcDatabaseProfile.DdlFamily.MYSQL && StringUtils.hasText(tableComment)) {
            sql += " COMMENT='" + escapeLiteral(tableComment) + "'";
        }
        return sql;
    }

    public List<String> tableComment(String namespace, String tableName, String tableComment) throws DbAccessException {
        if (!StringUtils.hasText(tableComment)) {
            return List.of();
        }
        String table = qualifiedTable(namespace, tableName);
        if (family == JdbcDatabaseProfile.DdlFamily.MYSQL) {
            return List.of("ALTER TABLE " + table + " COMMENT='" + escapeLiteral(tableComment) + "'");
        }
        return List.of("COMMENT ON TABLE " + table + " IS '" + escapeLiteral(tableComment) + "'");
    }

    public List<String> columnComment(
            String namespace,
            String tableName,
            DbTableColumnDefinition column
    ) throws DbAccessException {
        if (family == JdbcDatabaseProfile.DdlFamily.MYSQL || !StringUtils.hasText(column.getColumnComment())) {
            return List.of();
        }
        return List.of("COMMENT ON COLUMN "
                + qualifiedTable(namespace, tableName)
                + "."
                + quoteIdentifier(column.getColumnName())
                + " IS '"
                + escapeLiteral(column.getColumnComment())
                + "'");
    }

    public List<String> addColumn(
            String namespace,
            String tableName,
            DbTableColumnDefinition column
    ) throws DbAccessException {
        String table = qualifiedTable(namespace, tableName);
        String keyword = family == JdbcDatabaseProfile.DdlFamily.ORACLE ? " ADD " : " ADD COLUMN ";
        List<String> result = new ArrayList<>();
        result.add("ALTER TABLE " + table + keyword + columnDefinition(column, false));
        result.addAll(columnComment(namespace, tableName, column));
        return result;
    }

    public List<String> alterColumn(
            String namespace,
            String tableName,
            DbTableColumnDefinition column
    ) throws DbAccessException {
        validateColumn(column);
        String table = qualifiedTable(namespace, tableName);
        String name = quoteIdentifier(column.getColumnName());
        List<String> result = new ArrayList<>();
        if (family == JdbcDatabaseProfile.DdlFamily.MYSQL) {
            result.add("ALTER TABLE " + table + " MODIFY COLUMN " + columnDefinition(column, false));
            return result;
        }
        if (family == JdbcDatabaseProfile.DdlFamily.ORACLE) {
            result.add("ALTER TABLE " + table + " MODIFY (" + columnDefinition(column, false) + ")");
            result.addAll(columnComment(namespace, tableName, column));
            return result;
        }
        result.add("ALTER TABLE " + table + " ALTER COLUMN " + name + " TYPE " + dataType(column));
        result.add("ALTER TABLE " + table + " ALTER COLUMN " + name
                + (Boolean.FALSE.equals(column.getNullable()) ? " SET NOT NULL" : " DROP NOT NULL"));
        if (column.getDefaultValue() == null) {
            result.add("ALTER TABLE " + table + " ALTER COLUMN " + name + " DROP DEFAULT");
        } else {
            result.add("ALTER TABLE " + table + " ALTER COLUMN " + name + " SET DEFAULT "
                    + defaultValue(column.getDefaultValue()));
        }
        result.addAll(columnComment(namespace, tableName, column));
        return result;
    }

    public String dropColumn(String namespace, String tableName, String columnName) throws DbAccessException {
        return "ALTER TABLE " + qualifiedTable(namespace, tableName)
                + " DROP COLUMN " + quoteIdentifier(columnName);
    }

    public String addPrimaryKey(
            String namespace,
            String tableName,
            List<String> columnNames
    ) throws DbAccessException {
        if (columnNames == null || columnNames.isEmpty()) {
            throw new DbAccessException("主键字段不能为空");
        }
        List<String> quoted = new ArrayList<>();
        for (String columnName : columnNames) {
            quoted.add(quoteIdentifier(columnName));
        }
        return "ALTER TABLE " + qualifiedTable(namespace, tableName)
                + " ADD PRIMARY KEY (" + String.join(", ", quoted) + ")";
    }

    public String pagedQuery(String baseSql) {
        if (family == JdbcDatabaseProfile.DdlFamily.ORACLE) {
            return baseSql + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        }
        return baseSql + " LIMIT ? OFFSET ?";
    }

    public void validateColumn(DbTableColumnDefinition column) throws DbAccessException {
        if (column == null) {
            throw new DbAccessException("字段定义不能为空");
        }
        requireIdentifier(column.getColumnName(), "字段名");
        String dataType = normalizeDataType(column.getDataType());
        if (!DATA_TYPES.contains(dataType)) {
            throw new DbAccessException("字段类型不合法: " + column.getDataType());
        }
        if (Boolean.TRUE.equals(column.getAutoIncrement()) && column.getDefaultValue() != null) {
            throw new DbAccessException("自增字段不能同时配置 defaultValue");
        }
    }

    public String requireIdentifier(String value, String label) throws DbAccessException {
        if (!StringUtils.hasText(value) || !IDENTIFIER.matcher(value.trim()).matches()) {
            throw new DbAccessException(label + "不合法: " + value);
        }
        return value.trim();
    }

    private String columnDefinition(DbTableColumnDefinition column, boolean creatingTable) throws DbAccessException {
        validateColumn(column);
        StringBuilder builder = new StringBuilder(quoteIdentifier(column.getColumnName()))
                .append(" ")
                .append(dataType(column));
        if (Boolean.TRUE.equals(column.getAutoIncrement())) {
            if (family == JdbcDatabaseProfile.DdlFamily.MYSQL) {
                builder.append(" AUTO_INCREMENT");
            } else {
                builder.append(" GENERATED BY DEFAULT AS IDENTITY");
            }
        }
        if (column.getDefaultValue() != null) {
            builder.append(" DEFAULT ").append(defaultValue(column.getDefaultValue()));
        }
        builder.append(Boolean.FALSE.equals(column.getNullable()) ? " NOT NULL" : " NULL");
        if (family == JdbcDatabaseProfile.DdlFamily.MYSQL && StringUtils.hasText(column.getColumnComment())) {
            builder.append(" COMMENT '").append(escapeLiteral(column.getColumnComment())).append("'");
        }
        if (!creatingTable && Boolean.TRUE.equals(column.getPrimaryKey())) {
            // Existing-table primary keys are added once, after all requested columns are processed.
        }
        return builder.toString();
    }

    private String dataType(DbTableColumnDefinition column) throws DbAccessException {
        String type = normalizeDataType(column.getDataType());
        if (requiresLength(type)) {
            int length = column.getColumnLength() == null ? 255 : column.getColumnLength();
            return type + "(" + positive(length, "columnLength") + ")";
        }
        if (requiresPrecision(type)) {
            int precision = column.getColumnPrecision() == null ? 10 : column.getColumnPrecision();
            if (column.getColumnScale() != null) {
                return type + "(" + positive(precision, "columnPrecision") + ","
                        + nonNegative(column.getColumnScale(), "columnScale") + ")";
            }
            return type + "(" + positive(precision, "columnPrecision") + ")";
        }
        return type;
    }

    private boolean requiresLength(String type) {
        return "CHAR".equals(type)
                || "VARCHAR".equals(type)
                || "VARCHAR2".equals(type)
                || "NCHAR".equals(type)
                || "NVARCHAR".equals(type)
                || "NVARCHAR2".equals(type)
                || "BINARY".equals(type)
                || "VARBINARY".equals(type);
    }

    private boolean requiresPrecision(String type) {
        return "DECIMAL".equals(type) || "NUMERIC".equals(type) || "NUMBER".equals(type);
    }

    private String defaultValue(String value) {
        String trimmed = value == null ? "" : value.trim();
        if ("NULL".equalsIgnoreCase(trimmed)
                || "CURRENT_TIMESTAMP".equalsIgnoreCase(trimmed)
                || "CURRENT_DATE".equalsIgnoreCase(trimmed)
                || "CURRENT_USER".equalsIgnoreCase(trimmed)
                || "TRUE".equalsIgnoreCase(trimmed)
                || "FALSE".equalsIgnoreCase(trimmed)
                || NUMERIC_LITERAL.matcher(trimmed).matches()) {
            return trimmed;
        }
        return "'" + escapeLiteral(trimmed) + "'";
    }

    private int positive(Integer value, String label) throws DbAccessException {
        if (value == null || value < 1) {
            throw new DbAccessException(label + " 必须大于 0");
        }
        return value;
    }

    private int nonNegative(Integer value, String label) throws DbAccessException {
        if (value == null || value < 0) {
            throw new DbAccessException(label + " 不能小于 0");
        }
        return value;
    }

    private String normalizeDataType(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private String quoteIdentifierUnchecked(String value) {
        try {
            return quoteIdentifier(value);
        } catch (DbAccessException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private String escapeLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}
