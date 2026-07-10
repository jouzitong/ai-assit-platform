package ai.platform.aiassit.db.engine.executor.mysql.provider;

import ai.platform.aiassit.db.engine.executor.mysql.support.MysqlConnectionSupport;
import ai.platform.aiassit.db.engine.executor.mysql.support.MysqlSqlGuard;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.model.DbColumnMeta;
import ai.platform.aiassit.db.engine.executor.spi.model.DbQueryColumn;
import ai.platform.aiassit.db.engine.executor.spi.model.DbTableColumnDefinition;
import ai.platform.aiassit.db.engine.executor.spi.model.DbTableMeta;
import ai.platform.aiassit.db.engine.executor.spi.provider.DbAccessExecutor;
import ai.platform.aiassit.db.engine.executor.spi.request.DeleteTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ExecuteRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTablesRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.QueryRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.SaveTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.SaveTableRequest;
import ai.platform.aiassit.db.engine.executor.spi.result.DeleteTableColumnsResult;
import ai.platform.aiassit.db.engine.executor.spi.result.ExecuteResult;
import ai.platform.aiassit.db.engine.executor.spi.result.ListTableColumnsResult;
import ai.platform.aiassit.db.engine.executor.spi.result.ListTablesResult;
import ai.platform.aiassit.db.engine.executor.spi.result.QueryResult;
import ai.platform.aiassit.db.engine.executor.spi.result.SaveTableColumnsResult;
import ai.platform.aiassit.db.engine.executor.spi.result.SaveTableResult;
import ai.platform.aiassit.db.engine.executor.spi.result.TestConnectionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class MysqlDbAccessExecutor implements DbAccessExecutor {

    private static final String LIST_TABLES_SQL = """
            SELECT table_name, table_comment, table_type
            FROM information_schema.tables
            WHERE table_schema = ?
              AND (? IS NULL OR table_name LIKE CONCAT('%', ?, '%'))
            ORDER BY table_name
            """;

    private static final String LIST_COLUMNS_SQL = """
            SELECT c.table_name,
                   c.column_name,
                   c.data_type,
                   c.character_maximum_length,
                   c.numeric_precision,
                   c.numeric_scale,
                   c.is_nullable,
                   CASE WHEN k.column_name IS NULL THEN 0 ELSE 1 END AS primary_key,
                   c.column_default,
                   c.ordinal_position,
                   c.column_comment
            FROM information_schema.columns c
            LEFT JOIN information_schema.key_column_usage k
              ON c.table_schema = k.table_schema
             AND c.table_name = k.table_name
             AND c.column_name = k.column_name
             AND k.constraint_name = 'PRIMARY'
            WHERE c.table_schema = ?
              AND c.table_name = ?
            ORDER BY c.ordinal_position
            """;

    private final MysqlConnectionSupport connectionSupport;
    private final DbAccessContext context;

    public MysqlDbAccessExecutor(MysqlConnectionSupport connectionSupport, DbAccessContext context) {
        this.connectionSupport = connectionSupport;
        this.context = context;
    }

    @Override
    public TestConnectionResult testConnection() throws DbAccessException {
        try (Connection connection = connectionSupport.openConnection(context)) {
            DatabaseMetaData metaData = connection.getMetaData();
            return TestConnectionResult.builder()
                    .success(Boolean.TRUE)
                    .message("连接成功")
                    .databaseProductName(metaData.getDatabaseProductName())
                    .databaseProductVersion(metaData.getDatabaseProductVersion())
                    .catalog(connection.getCatalog())
                    .schema(connection.getSchema())
                    .build();
        } catch (SQLException ex) {
            throw new DbAccessException("MySQL 连接测试失败", ex);
        }
    }

    @Override
    public ListTablesResult listTables(ListTablesRequest request) throws DbAccessException {
        String schemaName = resolveSchemaName(request == null ? null : request.getSchemaName());
        Integer limit = request == null ? null : request.getLimit();
        String keyword = request == null ? null : request.getKeyword();
        String sql = LIST_TABLES_SQL + (limit != null && limit > 0 ? " LIMIT ?" : "");
        try (Connection connection = connectionSupport.openConnection(context);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, schemaName);
            if (StringUtils.hasText(keyword)) {
                statement.setString(index++, keyword.trim());
                statement.setString(index++, keyword.trim());
            } else {
                statement.setString(index++, null);
                statement.setString(index++, null);
            }
            if (limit != null && limit > 0) {
                statement.setInt(index, limit);
            }
            List<DbTableMeta> tables = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tables.add(DbTableMeta.builder()
                            .tableName(resultSet.getString("table_name"))
                            .tableComment(resultSet.getString("table_comment"))
                            .tableType(resultSet.getString("table_type"))
                            .build());
                }
            }
            return ListTablesResult.builder().tables(tables).build();
        } catch (SQLException ex) {
            throw new DbAccessException("查询 MySQL 数据表失败", ex);
        }
    }

    @Override
    public ListTableColumnsResult listTableColumns(ListTableColumnsRequest request) throws DbAccessException {
        if (request == null || !StringUtils.hasText(request.getTableName())) {
            throw new DbAccessException("tableName 不能为空");
        }
        String schemaName = resolveSchemaName(request.getSchemaName());
        try (Connection connection = connectionSupport.openConnection(context);
             PreparedStatement statement = connection.prepareStatement(LIST_COLUMNS_SQL)) {
            statement.setString(1, schemaName);
            statement.setString(2, request.getTableName().trim());
            List<DbColumnMeta> columns = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columns.add(DbColumnMeta.builder()
                            .tableName(resultSet.getString("table_name"))
                            .columnName(resultSet.getString("column_name"))
                            .dataType(resultSet.getString("data_type"))
                            .columnLength(getInteger(resultSet, "character_maximum_length"))
                            .columnPrecision(getInteger(resultSet, "numeric_precision"))
                            .columnScale(getInteger(resultSet, "numeric_scale"))
                            .nullable("YES".equalsIgnoreCase(resultSet.getString("is_nullable")))
                            .primaryKey(resultSet.getInt("primary_key") == 1)
                            .defaultValue(resultSet.getString("column_default"))
                            .ordinalPosition(getInteger(resultSet, "ordinal_position"))
                            .columnComment(resultSet.getString("column_comment"))
                            .build());
                }
            }
            return ListTableColumnsResult.builder()
                    .tableName(request.getTableName().trim())
                    .columns(columns)
                    .build();
        } catch (SQLException ex) {
            throw new DbAccessException("查询 MySQL 字段定义失败", ex);
        }
    }

    @Override
    public QueryResult query(QueryRequest request) throws DbAccessException {
        if (request == null) {
            throw new DbAccessException("查询请求不能为空");
        }
        String sql = MysqlSqlGuard.validateQuery(request.getSql());
        log.debug("查询 SQL: {}", sql);
        Instant start = Instant.now();
        try (Connection connection = connectionSupport.openConnection(context);
             Statement statement = connection.createStatement()) {
            if (request.getMaxRows() != null && request.getMaxRows() > 0) {
                statement.setMaxRows(request.getMaxRows());
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            List<DbQueryColumn> columns = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                ResultSetMetaData metaData = resultSet.getMetaData();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    columns.add(DbQueryColumn.builder()
                            .name(metaData.getColumnName(i))
                            .label(metaData.getColumnLabel(i))
                            .jdbcType(metaData.getColumnType(i))
                            .typeName(metaData.getColumnTypeName(i))
                            .build());
                }
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= metaData.getColumnCount(); i++) {
                        row.put(metaData.getColumnLabel(i), resultSet.getObject(i));
                    }
                    rows.add(row);
                }
            }
            return QueryResult.builder()
                    .columns(columns)
                    .rows(rows)
                    .rowCount(rows.size())
                    .executionMs(Duration.between(start, Instant.now()).toMillis())
                    .build();
        } catch (SQLException ex) {
            throw new DbAccessException("执行 MySQL 查询失败", ex);
        }
    }

    @Override
    public ExecuteResult execute(ExecuteRequest request) throws DbAccessException {
        if (request == null) {
            throw new DbAccessException("执行请求不能为空");
        }
        String sql = MysqlSqlGuard.validateExecute(request.getSql());
        Instant start = Instant.now();
        try (Connection connection = connectionSupport.openConnection(context);
             Statement statement = connection.createStatement()) {
            int affectedRows = statement.executeUpdate(sql);
            return ExecuteResult.builder()
                    .affectedRows(affectedRows)
                    .executionMs(Duration.between(start, Instant.now()).toMillis())
                    .build();
        } catch (SQLException ex) {
            throw new DbAccessException("执行 MySQL 更新失败", ex);
        }
    }

    @Override
    public SaveTableResult saveTable(SaveTableRequest request) throws DbAccessException {
        if (request == null || !StringUtils.hasText(request.getTableName())) {
            throw new DbAccessException("tableName 不能为空");
        }
        String schemaName = resolveSchemaName(request.getSchemaName());
        String tableName = request.getTableName().trim();
        List<String> executedSqls = new ArrayList<>();
        boolean created = false;
        boolean updated = false;
        try (Connection connection = connectionSupport.openConnection(context);
             Statement statement = connection.createStatement()) {
            if (!tableExists(connection, schemaName, tableName)) {
                String createSql = buildCreateTableSql(tableName, request.getTableComment(), request.getColumns());
                statement.execute(createSql);
                executedSqls.add(createSql);
                created = true;
            } else {
                if (StringUtils.hasText(request.getTableComment())) {
                    String commentSql = buildAlterTableCommentSql(tableName, request.getTableComment());
                    statement.execute(commentSql);
                    executedSqls.add(commentSql);
                    updated = true;
                }
                if (!CollectionUtils.isEmpty(request.getColumns())) {
                    SaveTableColumnsResult columnResult = saveTableColumnsInternal(connection, schemaName, tableName, request.getColumns());
                    executedSqls.addAll(columnResult.getExecutedSqls());
                    updated = updated || columnResult.getAffectedColumnCount() > 0;
                }
            }
            return SaveTableResult.builder()
                    .tableName(tableName)
                    .created(created)
                    .updated(updated)
                    .executedSqls(executedSqls)
                    .build();
        } catch (SQLException ex) {
            throw new DbAccessException("保存 MySQL 表结构失败", ex);
        }
    }

    @Override
    public SaveTableColumnsResult saveTableColumns(SaveTableColumnsRequest request) throws DbAccessException {
        if (request == null || !StringUtils.hasText(request.getTableName())) {
            throw new DbAccessException("tableName 不能为空");
        }
        String schemaName = resolveSchemaName(request.getSchemaName());
        try (Connection connection = connectionSupport.openConnection(context)) {
            return saveTableColumnsInternal(connection, schemaName, request.getTableName().trim(), request.getColumns());
        } catch (SQLException ex) {
            throw new DbAccessException("保存 MySQL 字段结构失败", ex);
        }
    }

    @Override
    public DeleteTableColumnsResult deleteTableColumns(DeleteTableColumnsRequest request) throws DbAccessException {
        if (request == null || !StringUtils.hasText(request.getTableName())) {
            throw new DbAccessException("tableName 不能为空");
        }
        if (CollectionUtils.isEmpty(request.getColumnNames())) {
            throw new DbAccessException("columnNames 不能为空");
        }
        String schemaName = resolveSchemaName(request.getSchemaName());
        String tableName = request.getTableName().trim();
        List<String> executedSqls = new ArrayList<>();
        try (Connection connection = connectionSupport.openConnection(context)) {
            if (!tableExists(connection, schemaName, tableName)) {
                throw new DbAccessException("数据表不存在: " + tableName);
            }
            Map<String, DbColumnMeta> existingColumns = loadExistingColumns(connection, schemaName, tableName);
            try (Statement statement = connection.createStatement()) {
                for (String columnName : request.getColumnNames()) {
                    if (!StringUtils.hasText(columnName)) {
                        continue;
                    }
                    String normalized = columnName.trim().toLowerCase(Locale.ROOT);
                    if (!existingColumns.containsKey(normalized)) {
                        throw new DbAccessException("字段不存在: " + columnName);
                    }
                    String sql = "ALTER TABLE `" + tableName + "` DROP COLUMN `" + columnName.trim() + "`";
                    statement.execute(sql);
                    executedSqls.add(sql);
                }
            }
            return DeleteTableColumnsResult.builder()
                    .tableName(tableName)
                    .affectedColumnCount(executedSqls.size())
                    .executedSqls(executedSqls)
                    .build();
        } catch (SQLException ex) {
            throw new DbAccessException("删除 MySQL 字段失败", ex);
        }
    }

    private SaveTableColumnsResult saveTableColumnsInternal(
            Connection connection,
            String schemaName,
            String tableName,
            List<DbTableColumnDefinition> columns
    ) throws SQLException, DbAccessException {
        if (CollectionUtils.isEmpty(columns)) {
            return SaveTableColumnsResult.builder()
                    .tableName(tableName)
                    .affectedColumnCount(0)
                    .executedSqls(List.of())
                    .build();
        }
        if (!tableExists(connection, schemaName, tableName)) {
            throw new DbAccessException("数据表不存在: " + tableName);
        }
        Map<String, DbColumnMeta> existingColumns = loadExistingColumns(connection, schemaName, tableName);
        List<String> executedSqls = new ArrayList<>();
        try (Statement statement = connection.createStatement()) {
            for (DbTableColumnDefinition column : columns) {
                validateColumn(column);
                String normalizedName = column.getColumnName().trim().toLowerCase(Locale.ROOT);
                String sql;
                if (existingColumns.containsKey(normalizedName)) {
                    sql = "ALTER TABLE `" + tableName + "` MODIFY COLUMN " + buildColumnDefinition(column);
                } else {
                    sql = "ALTER TABLE `" + tableName + "` ADD COLUMN " + buildColumnDefinition(column);
                }
                statement.execute(sql);
                executedSqls.add(sql);
            }
        }
        return SaveTableColumnsResult.builder()
                .tableName(tableName)
                .affectedColumnCount(executedSqls.size())
                .executedSqls(executedSqls)
                .build();
    }

    private boolean tableExists(Connection connection, String schemaName, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(1)
                FROM information_schema.tables
                WHERE table_schema = ?
                  AND table_name = ?
                """)) {
            statement.setString(1, schemaName);
            statement.setString(2, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    private Map<String, DbColumnMeta> loadExistingColumns(Connection connection, String schemaName, String tableName) throws SQLException {
        Map<String, DbColumnMeta> result = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(LIST_COLUMNS_SQL)) {
            statement.setString(1, schemaName);
            statement.setString(2, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    DbColumnMeta column = DbColumnMeta.builder()
                            .columnName(resultSet.getString("column_name"))
                            .build();
                    result.put(column.getColumnName().trim().toLowerCase(Locale.ROOT), column);
                }
            }
        }
        return result;
    }

    private String buildCreateTableSql(String tableName, String tableComment, List<DbTableColumnDefinition> columns) throws DbAccessException {
        if (CollectionUtils.isEmpty(columns)) {
            throw new DbAccessException("创建表时字段列表不能为空");
        }
        List<DbTableColumnDefinition> primaryColumns = columns.stream()
                .filter(column -> Boolean.TRUE.equals(column.getPrimaryKey()))
                .toList();
        String columnSql = columns.stream()
                .peek(this::validateColumnUnchecked)
                .map(this::buildColumnDefinitionUnchecked)
                .collect(Collectors.joining(", "));
        StringBuilder builder = new StringBuilder("CREATE TABLE `")
                .append(tableName)
                .append("` (")
                .append(columnSql);
        if (!primaryColumns.isEmpty()) {
            builder.append(", PRIMARY KEY (")
                    .append(primaryColumns.stream()
                            .map(column -> "`" + column.getColumnName().trim() + "`")
                            .collect(Collectors.joining(", ")))
                    .append(")");
        }
        builder.append(")");
        if (StringUtils.hasText(tableComment)) {
            builder.append(" COMMENT='").append(escapeSqlLiteral(tableComment)).append("'");
        }
        return builder.toString();
    }

    private String buildAlterTableCommentSql(String tableName, String tableComment) {
        return "ALTER TABLE `" + tableName + "` COMMENT='" + escapeSqlLiteral(tableComment) + "'";
    }

    private void validateColumn(DbTableColumnDefinition column) throws DbAccessException {
        if (column == null || !StringUtils.hasText(column.getColumnName()) || !StringUtils.hasText(column.getDataType())) {
            throw new DbAccessException("字段名和字段类型不能为空");
        }
    }

    private void validateColumnUnchecked(DbTableColumnDefinition column) {
        try {
            validateColumn(column);
        } catch (DbAccessException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private String buildColumnDefinition(DbTableColumnDefinition column) throws DbAccessException {
        validateColumn(column);
        return buildColumnDefinitionUnchecked(column);
    }

    private String buildColumnDefinitionUnchecked(DbTableColumnDefinition column) {
        StringBuilder builder = new StringBuilder("`")
                .append(column.getColumnName().trim())
                .append("` ")
                .append(buildDataTypeSql(column));
        if (Boolean.FALSE.equals(column.getNullable())) {
            builder.append(" NOT NULL");
        } else {
            builder.append(" NULL");
        }
        if (column.getDefaultValue() != null) {
            builder.append(" DEFAULT ").append(resolveDefaultValueSql(column.getDefaultValue()));
        }
        if (Boolean.TRUE.equals(column.getAutoIncrement())) {
            builder.append(" AUTO_INCREMENT");
        }
        if (StringUtils.hasText(column.getColumnComment())) {
            builder.append(" COMMENT '").append(escapeSqlLiteral(column.getColumnComment())).append("'");
        }
        return builder.toString();
    }

    private String buildDataTypeSql(DbTableColumnDefinition column) {
        String dataType = column.getDataType().trim().toUpperCase(Locale.ROOT);
        if (requiresLength(dataType)) {
            int length = column.getColumnLength() == null ? 255 : column.getColumnLength();
            return dataType + "(" + length + ")";
        }
        if (requiresPrecision(dataType)) {
            int precision = column.getColumnPrecision() == null ? 10 : column.getColumnPrecision();
            if (column.getColumnScale() != null) {
                return dataType + "(" + precision + "," + column.getColumnScale() + ")";
            }
            return dataType + "(" + precision + ")";
        }
        return dataType;
    }

    private boolean requiresLength(String dataType) {
        return "CHAR".equals(dataType)
                || "VARCHAR".equals(dataType)
                || "BINARY".equals(dataType)
                || "VARBINARY".equals(dataType);
    }

    private boolean requiresPrecision(String dataType) {
        return "DECIMAL".equals(dataType) || "NUMERIC".equals(dataType);
    }

    private String resolveDefaultValueSql(String defaultValue) {
        String trimmed = defaultValue.trim();
        if ("NULL".equalsIgnoreCase(trimmed)
                || "CURRENT_TIMESTAMP".equalsIgnoreCase(trimmed)
                || trimmed.matches("-?\\d+(\\.\\d+)?")) {
            return trimmed;
        }
        return "'" + escapeSqlLiteral(trimmed) + "'";
    }

    private String escapeSqlLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private Integer getInteger(ResultSet resultSet, String columnLabel) throws SQLException {
        Object value = resultSet.getObject(columnLabel);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private String resolveSchemaName(String requestedSchema) throws DbAccessException {
        if (StringUtils.hasText(requestedSchema)) {
            return requestedSchema.trim();
        }
        if (context.getDatabase() != null) {
            if (StringUtils.hasText(context.getDatabase().getSchemaName())) {
                return context.getDatabase().getSchemaName().trim();
            }
            if (StringUtils.hasText(context.getDatabase().getDatabaseName())) {
                return context.getDatabase().getDatabaseName().trim();
            }
            String databaseNameFromJdbcUrl = resolveDatabaseNameFromJdbcUrl(context.getDatabase().getJdbcUrl());
            if (StringUtils.hasText(databaseNameFromJdbcUrl)) {
                return databaseNameFromJdbcUrl;
            }
        }
        throw new DbAccessException("缺少 schema/database 配置");
    }

    private String resolveDatabaseNameFromJdbcUrl(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return null;
        }
        String normalized = jdbcUrl.trim();
        int protocolIndex = normalized.indexOf("://");
        if (protocolIndex < 0) {
            return null;
        }
        String tail = normalized.substring(protocolIndex + 3);
        int slashIndex = tail.indexOf('/');
        if (slashIndex < 0 || slashIndex == tail.length() - 1) {
            return null;
        }
        String path = tail.substring(slashIndex + 1);
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        int paramIndex = path.indexOf(';');
        if (paramIndex >= 0) {
            path = path.substring(0, paramIndex);
        }
        return StringUtils.hasText(path) ? path.trim() : null;
    }
}
