package ai.platform.aiassit.db.engine.executor.jdbc.provider;

import ai.platform.aiassit.db.engine.executor.jdbc.support.JdbcConnectionSupport;
import ai.platform.aiassit.db.engine.executor.jdbc.support.JdbcDatabaseProfile;
import ai.platform.aiassit.db.engine.executor.jdbc.support.JdbcDdlSupport;
import ai.platform.aiassit.db.engine.executor.jdbc.support.JdbcSqlGuard;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.model.DbColumnMeta;
import ai.platform.aiassit.db.engine.executor.spi.model.DbIndexMeta;
import ai.platform.aiassit.db.engine.executor.spi.model.DbQueryColumn;
import ai.platform.aiassit.db.engine.executor.spi.model.DbTableColumnDefinition;
import ai.platform.aiassit.db.engine.executor.spi.model.DbTableMeta;
import ai.platform.aiassit.db.engine.executor.spi.provider.DbAccessExecutor;
import ai.platform.aiassit.db.engine.executor.spi.request.DeleteTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ExecuteRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTableIndexesRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTablesRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.QueryRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.SaveTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.SaveTableRequest;
import ai.platform.aiassit.db.engine.executor.spi.result.DeleteTableColumnsResult;
import ai.platform.aiassit.db.engine.executor.spi.result.ExecuteResult;
import ai.platform.aiassit.db.engine.executor.spi.result.ListTableColumnsResult;
import ai.platform.aiassit.db.engine.executor.spi.result.ListTableIndexesResult;
import ai.platform.aiassit.db.engine.executor.spi.result.ListTablesResult;
import ai.platform.aiassit.db.engine.executor.spi.result.QueryResult;
import ai.platform.aiassit.db.engine.executor.spi.result.SaveTableColumnsResult;
import ai.platform.aiassit.db.engine.executor.spi.result.SaveTableResult;
import ai.platform.aiassit.db.engine.executor.spi.result.TestConnectionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
public class JdbcDbAccessExecutor implements DbAccessExecutor {

    private static final String[] TABLE_TYPES = {"TABLE", "VIEW"};

    private final JdbcConnectionSupport connectionSupport;
    private final DbAccessContext context;
    private final JdbcDatabaseProfile profile;
    private final JdbcDdlSupport ddl;

    public JdbcDbAccessExecutor(
            JdbcConnectionSupport connectionSupport,
            DbAccessContext context
    ) throws DbAccessException {
        this.connectionSupport = connectionSupport;
        this.context = context;
        this.profile = JdbcDatabaseProfile.require(context == null ? null : context.getDbType());
        this.ddl = new JdbcDdlSupport(profile.resolveFamily(context));
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
                    .schema(safeSchema(connection))
                    .build();
        } catch (SQLException ex) {
            throw databaseException("连接测试失败", ex);
        }
    }

    @Override
    public ListTablesResult listTables(ListTablesRequest request) throws DbAccessException {
        String keyword = request == null ? null : request.getKeyword();
        Integer limit = request == null ? null : request.getLimit();
        List<DbTableMeta> tables = new ArrayList<>();
        try (Connection connection = connectionSupport.openConnection(context)) {
            Namespace namespace = resolveNamespace(connection, request == null ? null : request.getSchemaName());
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet resultSet = metadata.getTables(
                    namespace.catalog(), metadataPattern(metadata, namespace.schema()), "%", TABLE_TYPES)) {
                while (resultSet.next()) {
                    String tableName = resultSet.getString("TABLE_NAME");
                    if (StringUtils.hasText(keyword)
                            && (tableName == null || !tableName.toLowerCase(Locale.ROOT)
                            .contains(keyword.trim().toLowerCase(Locale.ROOT)))) {
                        continue;
                    }
                    tables.add(DbTableMeta.builder()
                            .tableName(tableName)
                            .tableComment(resultSet.getString("REMARKS"))
                            .tableType(resultSet.getString("TABLE_TYPE"))
                            .build());
                }
            }
            tables.sort(Comparator.comparing(
                    DbTableMeta::getTableName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            ));
            if (limit != null && limit > 0 && tables.size() > limit) {
                tables = new ArrayList<>(tables.subList(0, limit));
            }
            return ListTablesResult.builder().tables(tables).build();
        } catch (SQLException ex) {
            throw databaseException("查询数据表失败", ex);
        }
    }

    @Override
    public ListTableColumnsResult listTableColumns(ListTableColumnsRequest request) throws DbAccessException {
        String requestedTableName = requireTableName(request == null ? null : request.getTableName());
        try (Connection connection = connectionSupport.openConnection(context)) {
            Namespace namespace = resolveNamespace(connection, request.getSchemaName());
            String tableName = firstText(resolveExistingTableName(connection, namespace, requestedTableName), requestedTableName);
            List<DbColumnMeta> columns = loadColumns(connection, namespace, tableName);
            return ListTableColumnsResult.builder().tableName(tableName).columns(columns).build();
        } catch (SQLException ex) {
            throw databaseException("查询字段定义失败", ex);
        }
    }

    @Override
    public ListTableIndexesResult listTableIndexes(ListTableIndexesRequest request) throws DbAccessException {
        String requestedTableName = requireTableName(request == null ? null : request.getTableName());
        List<DbIndexMeta> indexes = new ArrayList<>();
        try (Connection connection = connectionSupport.openConnection(context)) {
            Namespace namespace = resolveNamespace(connection, request.getSchemaName());
            String tableName = firstText(resolveExistingTableName(connection, namespace, requestedTableName), requestedTableName);
            DatabaseMetaData metadata = connection.getMetaData();
            PrimaryKeyInfo primaryKey = loadPrimaryKeyInfo(metadata, namespace, tableName);
            try (ResultSet resultSet = metadata.getIndexInfo(
                    namespace.catalog(),
                    namespace.schema(),
                    tableName,
                    false,
                    false
            )) {
                while (resultSet.next()) {
                    short type = resultSet.getShort("TYPE");
                    if (type == DatabaseMetaData.tableIndexStatistic) {
                        continue;
                    }
                    String columnName = resultSet.getString("COLUMN_NAME");
                    String indexName = resultSet.getString("INDEX_NAME");
                    boolean unique = !resultSet.getBoolean("NON_UNIQUE");
                    boolean primary = StringUtils.hasText(primaryKey.indexName())
                            ? primaryKey.indexName().equalsIgnoreCase(indexName)
                            : "PRIMARY".equalsIgnoreCase(indexName);
                    indexes.add(DbIndexMeta.builder()
                            .tableName(tableName)
                            .indexName(indexName)
                            .indexType(indexTypeName(type))
                            .uniqueFlag(unique)
                            .primaryFlag(primary)
                            .columnName(columnName)
                            .columnOrder(getInteger(resultSet, "ORDINAL_POSITION"))
                            .build());
                }
            }
            indexes.sort(Comparator
                    .comparing(DbIndexMeta::getIndexName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(DbIndexMeta::getColumnOrder, Comparator.nullsLast(Integer::compareTo)));
            return ListTableIndexesResult.builder().tableName(tableName).indexes(indexes).build();
        } catch (SQLException ex) {
            throw databaseException("查询索引定义失败", ex);
        }
    }

    @Override
    public QueryResult query(QueryRequest request) throws DbAccessException {
        if (request == null) {
            throw new DbAccessException("查询请求不能为空");
        }
        String sql = JdbcSqlGuard.validateQuery(request.getSql());
        log.debug("{} 查询 SQL: {}", context.getDbType(), sql);
        Instant start = Instant.now();
        try (Connection connection = connectionSupport.openConnection(context);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (request.getMaxRows() != null && request.getMaxRows() > 0) {
                statement.setMaxRows(request.getMaxRows());
            }
            bindParameters(statement, request.getParameters());
            List<Map<String, Object>> rows = new ArrayList<>();
            List<DbQueryColumn> columns = new ArrayList<>();
            List<String> columnKeys = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                ResultSetMetaData metadata = resultSet.getMetaData();
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    String key = uniqueColumnKey(columnKeys, metadata.getColumnLabel(index), index);
                    columnKeys.add(key);
                    columns.add(DbQueryColumn.builder()
                            .name(metadata.getColumnName(index))
                            .label(key)
                            .jdbcType(metadata.getColumnType(index))
                            .typeName(metadata.getColumnTypeName(index))
                            .build());
                }
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int index = 1; index <= metadata.getColumnCount(); index++) {
                        row.put(columnKeys.get(index - 1), jsonFriendlyValue(resultSet.getObject(index)));
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
            throw databaseException("执行查询失败", ex);
        }
    }

    @Override
    public ExecuteResult execute(ExecuteRequest request) throws DbAccessException {
        if (request == null) {
            throw new DbAccessException("执行请求不能为空");
        }
        String sql = JdbcSqlGuard.validateExecute(request.getSql());
        Instant start = Instant.now();
        try (Connection connection = connectionSupport.openConnection(context);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParameters(statement, request.getParameters());
            int affectedRows = statement.executeUpdate();
            return ExecuteResult.builder()
                    .affectedRows(affectedRows)
                    .executionMs(Duration.between(start, Instant.now()).toMillis())
                    .build();
        } catch (SQLException ex) {
            throw databaseException("执行更新失败", ex);
        }
    }

    @Override
    public SaveTableResult saveTable(SaveTableRequest request) throws DbAccessException {
        String requestedTableName = requireTableName(request == null ? null : request.getTableName());
        String tableName = requestedTableName;
        List<String> executed = new ArrayList<>();
        boolean created = false;
        boolean updated = false;
        try (Connection connection = connectionSupport.openConnection(context)) {
            Namespace namespace = resolveNamespace(connection, request.getSchemaName());
            String existingTableName = resolveExistingTableName(connection, namespace, requestedTableName);
            if (existingTableName == null) {
                executeDdl(connection, ddl.createTable(namespace.ddlNamespace(), tableName,
                        request.getTableComment(), request.getColumns()), executed);
                if (profile.resolveFamily(context) != JdbcDatabaseProfile.DdlFamily.MYSQL) {
                    executeDdl(connection, ddl.tableComment(namespace.ddlNamespace(), tableName,
                            request.getTableComment()), executed);
                    if (!CollectionUtils.isEmpty(request.getColumns())) {
                        for (DbTableColumnDefinition column : request.getColumns()) {
                            executeDdl(connection, ddl.columnComment(namespace.ddlNamespace(), tableName, column), executed);
                        }
                    }
                }
                created = true;
            } else {
                tableName = existingTableName;
                if (StringUtils.hasText(request.getTableComment())) {
                    executeDdl(connection, ddl.tableComment(namespace.ddlNamespace(), tableName,
                            request.getTableComment()), executed);
                    updated = true;
                }
                if (!CollectionUtils.isEmpty(request.getColumns())) {
                    SaveTableColumnsResult result = saveTableColumnsInternal(
                            connection, namespace, tableName, request.getColumns());
                    executed.addAll(result.getExecutedSqls());
                    updated = updated || result.getAffectedColumnCount() > 0;
                }
            }
            return SaveTableResult.builder()
                    .tableName(tableName)
                    .created(created)
                    .updated(updated)
                    .executedSqls(executed)
                    .build();
        } catch (SQLException ex) {
            throw databaseException("保存表结构失败", ex);
        }
    }

    @Override
    public SaveTableColumnsResult saveTableColumns(SaveTableColumnsRequest request) throws DbAccessException {
        String tableName = requireTableName(request == null ? null : request.getTableName());
        try (Connection connection = connectionSupport.openConnection(context)) {
            Namespace namespace = resolveNamespace(connection, request.getSchemaName());
            return saveTableColumnsInternal(connection, namespace, tableName, request.getColumns());
        } catch (SQLException ex) {
            throw databaseException("保存字段结构失败", ex);
        }
    }

    @Override
    public DeleteTableColumnsResult deleteTableColumns(DeleteTableColumnsRequest request) throws DbAccessException {
        String tableName = requireTableName(request == null ? null : request.getTableName());
        if (CollectionUtils.isEmpty(request.getColumnNames())) {
            throw new DbAccessException("columnNames 不能为空");
        }
        List<String> executed = new ArrayList<>();
        try (Connection connection = connectionSupport.openConnection(context)) {
            Namespace namespace = resolveNamespace(connection, request.getSchemaName());
            String existingTableName = resolveExistingTableName(connection, namespace, tableName);
            if (existingTableName == null) {
                throw new DbAccessException("数据表不存在: " + tableName);
            }
            tableName = existingTableName;
            Map<String, DbColumnMeta> existing = loadColumnMap(connection, namespace, tableName);
            for (String columnName : request.getColumnNames()) {
                String validated = ddl.requireIdentifier(columnName, "字段名");
                if (!existing.containsKey(validated.toLowerCase(Locale.ROOT))) {
                    throw new DbAccessException("字段不存在: " + validated);
                }
                executeDdl(connection, ddl.dropColumn(namespace.ddlNamespace(), tableName, validated), executed);
            }
            return DeleteTableColumnsResult.builder()
                    .tableName(tableName)
                    .affectedColumnCount(executed.size())
                    .executedSqls(executed)
                    .build();
        } catch (SQLException ex) {
            throw databaseException("删除字段失败", ex);
        }
    }

    private SaveTableColumnsResult saveTableColumnsInternal(
            Connection connection,
            Namespace namespace,
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
        String existingTableName = resolveExistingTableName(connection, namespace, tableName);
        if (existingTableName == null) {
            throw new DbAccessException("数据表不存在: " + tableName);
        }
        tableName = existingTableName;
        Map<String, DbColumnMeta> existing = loadColumnMap(connection, namespace, tableName);
        Set<String> primaryKeys = loadPrimaryKeyColumns(connection.getMetaData(), namespace, tableName);
        List<String> executed = new ArrayList<>();
        List<String> requestedPrimaryKeys = new ArrayList<>();
        for (DbTableColumnDefinition column : columns) {
            if (column == null || !StringUtils.hasText(column.getColumnName())) {
                throw new DbAccessException("字段定义和字段名不能为空");
            }
            String normalized = column.getColumnName().trim().toLowerCase(Locale.ROOT);
            DbColumnMeta existingColumn = existing.get(normalized);
            if (existingColumn != null
                    && column.getPrimaryKey() != null
                    && !column.getPrimaryKey().equals(existingColumn.getPrimaryKey())) {
                throw new DbAccessException("暂不支持修改已有主键字段: " + column.getColumnName());
            }
            if (existingColumn == null && Boolean.TRUE.equals(column.getPrimaryKey()) && !primaryKeys.isEmpty()) {
                throw new DbAccessException("数据表已有主键，暂不支持新增主键字段: " + column.getColumnName());
            }
            DbTableColumnDefinition effectiveColumn = existingColumn == null
                    ? column : mergeColumnDefinition(column, existingColumn);
            ddl.validateColumn(effectiveColumn);
            List<String> statements = existingColumn != null
                    ? ddl.alterColumn(namespace.ddlNamespace(), tableName, effectiveColumn)
                    : ddl.addColumn(namespace.ddlNamespace(), tableName, effectiveColumn);
            executeDdl(connection, statements, executed);
            if (Boolean.TRUE.equals(effectiveColumn.getPrimaryKey())) {
                requestedPrimaryKeys.add(effectiveColumn.getColumnName().trim());
            }
        }
        if (primaryKeys.isEmpty() && !requestedPrimaryKeys.isEmpty()) {
            executeDdl(connection, ddl.addPrimaryKey(namespace.ddlNamespace(), tableName, requestedPrimaryKeys), executed);
        }
        return SaveTableColumnsResult.builder()
                .tableName(tableName)
                .affectedColumnCount(columns.size())
                .executedSqls(executed)
                .build();
    }

    private DbTableColumnDefinition mergeColumnDefinition(
            DbTableColumnDefinition incoming,
            DbColumnMeta existing
    ) {
        return DbTableColumnDefinition.builder()
                .columnName(incoming.getColumnName())
                .dataType(StringUtils.hasText(incoming.getDataType()) ? incoming.getDataType() : existing.getDataType())
                .columnLength(incoming.getColumnLength() == null ? existing.getColumnLength() : incoming.getColumnLength())
                .columnPrecision(incoming.getColumnPrecision() == null
                        ? existing.getColumnPrecision() : incoming.getColumnPrecision())
                .columnScale(incoming.getColumnScale() == null ? existing.getColumnScale() : incoming.getColumnScale())
                .nullable(incoming.getNullable() == null ? existing.getNullable() : incoming.getNullable())
                .primaryKey(incoming.getPrimaryKey() == null ? existing.getPrimaryKey() : incoming.getPrimaryKey())
                .defaultValue(incoming.getDefaultValue() == null ? existing.getDefaultValue() : incoming.getDefaultValue())
                .columnComment(incoming.getColumnComment() == null
                        ? existing.getColumnComment() : incoming.getColumnComment())
                .autoIncrement(incoming.getAutoIncrement() == null
                        ? existing.getAutoIncrement() : incoming.getAutoIncrement())
                .build();
    }

    private List<DbColumnMeta> loadColumns(
            Connection connection,
            Namespace namespace,
            String tableName
    ) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        Set<String> primaryKeys = loadPrimaryKeyColumns(metadata, namespace, tableName);
        List<DbColumnMeta> columns = new ArrayList<>();
        String escapedTableName = escapeMetadataPattern(metadata, tableName);
        try (ResultSet resultSet = metadata.getColumns(
                namespace.catalog(), metadataPattern(metadata, namespace.schema()), escapedTableName, "%")) {
            while (resultSet.next()) {
                String columnName = resultSet.getString("COLUMN_NAME");
                int jdbcType = resultSet.getInt("DATA_TYPE");
                Integer size = getInteger(resultSet, "COLUMN_SIZE");
                int nullability = resultSet.getInt("NULLABLE");
                columns.add(DbColumnMeta.builder()
                        .tableName(tableName)
                        .columnName(columnName)
                        .dataType(resultSet.getString("TYPE_NAME"))
                        .columnLength(isSizedType(jdbcType) ? size : null)
                        .columnPrecision(isNumericType(jdbcType) ? size : null)
                        .columnScale(getInteger(resultSet, "DECIMAL_DIGITS"))
                        .nullable(nullability == DatabaseMetaData.columnNullableUnknown
                                ? null : nullability == DatabaseMetaData.columnNullable)
                        .primaryKey(containsIgnoreCase(primaryKeys, columnName))
                        .defaultValue(resultSet.getString("COLUMN_DEF"))
                        .ordinalPosition(getInteger(resultSet, "ORDINAL_POSITION"))
                        .columnComment(resultSet.getString("REMARKS"))
                        .autoIncrement("YES".equalsIgnoreCase(safeString(resultSet, "IS_AUTOINCREMENT")))
                        .build());
            }
        }
        return columns;
    }

    private Map<String, DbColumnMeta> loadColumnMap(
            Connection connection,
            Namespace namespace,
            String tableName
    ) throws SQLException {
        Map<String, DbColumnMeta> result = new LinkedHashMap<>();
        for (DbColumnMeta column : loadColumns(connection, namespace, tableName)) {
            result.put(column.getColumnName().toLowerCase(Locale.ROOT), column);
        }
        return result;
    }

    private Set<String> loadPrimaryKeyColumns(
            DatabaseMetaData metadata,
            Namespace namespace,
            String tableName
    ) throws SQLException {
        return loadPrimaryKeyInfo(metadata, namespace, tableName).columns();
    }

    private PrimaryKeyInfo loadPrimaryKeyInfo(
            DatabaseMetaData metadata,
            Namespace namespace,
            String tableName
    ) throws SQLException {
        Set<String> result = new LinkedHashSet<>();
        String indexName = null;
        try (ResultSet resultSet = metadata.getPrimaryKeys(namespace.catalog(), namespace.schema(), tableName)) {
            while (resultSet.next()) {
                result.add(resultSet.getString("COLUMN_NAME"));
                if (indexName == null) {
                    indexName = resultSet.getString("PK_NAME");
                }
            }
        }
        return new PrimaryKeyInfo(result, indexName);
    }

    private String resolveExistingTableName(
            Connection connection,
            Namespace namespace,
            String tableName
    ) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String caseInsensitiveMatch = null;
        try (ResultSet resultSet = metadata.getTables(
                namespace.catalog(), metadataPattern(metadata, namespace.schema()), "%", TABLE_TYPES)) {
            while (resultSet.next()) {
                String candidate = resultSet.getString("TABLE_NAME");
                if (tableName.equals(candidate)) {
                    return candidate;
                }
                if (caseInsensitiveMatch == null && tableName.equalsIgnoreCase(candidate)) {
                    caseInsensitiveMatch = candidate;
                }
            }
        }
        return caseInsensitiveMatch;
    }

    private Namespace resolveNamespace(Connection connection, String requestedSchema) throws SQLException, DbAccessException {
        JdbcDatabaseProfile.DdlFamily family = profile.resolveFamily(context);
        String configuredSchema = context.getDatabase() == null ? null : context.getDatabase().getSchemaName();
        String databaseName = context.getDatabase() == null ? null : context.getDatabase().getDatabaseName();
        if (family == JdbcDatabaseProfile.DdlFamily.MYSQL) {
            String catalog = firstText(requestedSchema, configuredSchema, databaseName, connection.getCatalog());
            if (!StringUtils.hasText(catalog)) {
                throw new DbAccessException("缺少 database/catalog 配置");
            }
            ddl.requireIdentifier(catalog, "database/catalog");
            return new Namespace(catalog, null, catalog);
        }
        String schema = firstText(requestedSchema, configuredSchema, safeSchema(connection));
        if (!StringUtils.hasText(schema)) {
            DbAccessAuthFallback auth = new DbAccessAuthFallback(context);
            schema = auth.username();
        }
        if (!StringUtils.hasText(schema)) {
            throw new DbAccessException("缺少 schema 配置");
        }
        ddl.requireIdentifier(schema, "schema");
        String actualSchema = resolveSchemaCase(connection.getMetaData(), schema);
        return new Namespace(connection.getCatalog(), actualSchema, actualSchema);
    }

    private void executeDdl(Connection connection, String sql, List<String> executed) throws SQLException {
        if (!StringUtils.hasText(sql)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            executed.add(sql);
        } catch (SQLException ex) {
            throw new SQLException("执行 DDL 失败，当前调用已成功执行 " + executed.size() + " 条；失败语句: " + sql, ex);
        }
    }

    private void executeDdl(Connection connection, List<String> sqls, List<String> executed) throws SQLException {
        if (sqls == null) {
            return;
        }
        for (String sql : sqls) {
            executeDdl(connection, sql, executed);
        }
    }

    private void bindParameters(PreparedStatement statement, List<Object> parameters) throws SQLException {
        if (CollectionUtils.isEmpty(parameters)) {
            return;
        }
        for (int index = 0; index < parameters.size(); index++) {
            statement.setObject(index + 1, parameters.get(index));
        }
    }

    private Object jsonFriendlyValue(Object value) throws SQLException {
        try {
            if (value instanceof Clob clob) {
                try {
                    try (Reader reader = clob.getCharacterStream()) {
                        return readCharacterStream(reader);
                    }
                } finally {
                    clob.free();
                }
            }
            if (value instanceof Blob blob) {
                try {
                    try (InputStream input = blob.getBinaryStream()) {
                        return input.readAllBytes();
                    }
                } finally {
                    blob.free();
                }
            }
            if (value instanceof SQLXML sqlxml) {
                try {
                    return sqlxml.getString();
                } finally {
                    sqlxml.free();
                }
            }
            if (value instanceof Array array) {
                try {
                    Object arrayValue = array.getArray();
                    return arrayValue instanceof Object[] objects ? Arrays.asList(objects) : arrayValue;
                } finally {
                    array.free();
                }
            }
            if (value == null
                    || value instanceof String
                    || value instanceof Number
                    || value instanceof Boolean
                    || value instanceof byte[]
                    || value instanceof java.util.Date
                    || value instanceof java.time.temporal.TemporalAccessor) {
                return value;
            }
            return String.valueOf(value);
        } catch (IOException ex) {
            throw new SQLException("读取 JDBC 大字段失败", ex);
        }
    }

    private String readCharacterStream(Reader reader) throws IOException {
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[4_096];
        int count;
        while ((count = reader.read(buffer)) >= 0) {
            result.append(buffer, 0, count);
        }
        return result.toString();
    }

    private String uniqueColumnKey(List<String> existingKeys, String label, int columnIndex) {
        String base = StringUtils.hasText(label) ? label : "column_" + columnIndex;
        if (!existingKeys.contains(base)) {
            return base;
        }
        int suffix = 2;
        while (existingKeys.contains(base + "_" + suffix)) {
            suffix++;
        }
        return base + "_" + suffix;
    }

    private String requireTableName(String tableName) throws DbAccessException {
        return ddl.requireIdentifier(tableName, "tableName");
    }

    private Integer getInteger(ResultSet resultSet, String label) throws SQLException {
        Object value = resultSet.getObject(label);
        if (value == null) {
            return null;
        }
        return value instanceof Number number ? number.intValue() : Integer.valueOf(String.valueOf(value));
    }

    private String safeString(ResultSet resultSet, String label) {
        try {
            return resultSet.getString(label);
        } catch (SQLException ignored) {
            return null;
        }
    }

    private boolean isNumericType(int jdbcType) {
        return jdbcType == java.sql.Types.DECIMAL || jdbcType == java.sql.Types.NUMERIC;
    }

    private boolean isSizedType(int jdbcType) {
        return jdbcType == java.sql.Types.CHAR
                || jdbcType == java.sql.Types.VARCHAR
                || jdbcType == java.sql.Types.LONGVARCHAR
                || jdbcType == java.sql.Types.NCHAR
                || jdbcType == java.sql.Types.NVARCHAR
                || jdbcType == java.sql.Types.LONGNVARCHAR
                || jdbcType == java.sql.Types.BINARY
                || jdbcType == java.sql.Types.VARBINARY
                || jdbcType == java.sql.Types.LONGVARBINARY;
    }

    private String indexTypeName(short type) {
        return switch (type) {
            case DatabaseMetaData.tableIndexClustered -> "CLUSTERED";
            case DatabaseMetaData.tableIndexHashed -> "HASHED";
            case DatabaseMetaData.tableIndexOther -> "OTHER";
            default -> "UNKNOWN";
        };
    }

    private String escapeMetadataPattern(DatabaseMetaData metadata, String value) throws SQLException {
        String escape = metadata.getSearchStringEscape();
        if (!StringUtils.hasText(escape)) {
            return value;
        }
        return value.replace(escape, escape + escape)
                .replace("_", escape + "_")
                .replace("%", escape + "%");
    }

    private String metadataPattern(DatabaseMetaData metadata, String value) throws SQLException {
        return value == null ? null : escapeMetadataPattern(metadata, value);
    }

    private String resolveSchemaCase(DatabaseMetaData metadata, String requestedSchema) {
        String caseInsensitiveMatch = null;
        try (ResultSet schemas = metadata.getSchemas()) {
            while (schemas.next()) {
                String candidate = schemas.getString("TABLE_SCHEM");
                if (requestedSchema.equals(candidate)) {
                    return candidate;
                }
                if (caseInsensitiveMatch == null && requestedSchema.equalsIgnoreCase(candidate)) {
                    caseInsensitiveMatch = candidate;
                }
            }
        } catch (SQLException ignored) {
            return requestedSchema;
        }
        return caseInsensitiveMatch == null ? requestedSchema : caseInsensitiveMatch;
    }

    private boolean containsIgnoreCase(Set<String> values, String candidate) {
        if (candidate == null || values == null) {
            return false;
        }
        for (String value : values) {
            if (candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private String safeSchema(Connection connection) {
        try {
            return connection.getSchema();
        } catch (SQLException | AbstractMethodError ignored) {
            return null;
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private DbAccessException databaseException(String action, SQLException ex) {
        return new DbAccessException(context.getDbType() + " " + action, ex);
    }

    private record Namespace(String catalog, String schema, String ddlNamespace) {
    }

    private record PrimaryKeyInfo(Set<String> columns, String indexName) {
    }

    private record DbAccessAuthFallback(String username) {
        private DbAccessAuthFallback(DbAccessContext context) {
            this(context == null || context.getAuth() == null ? null : context.getAuth().getUsername());
        }
    }
}
