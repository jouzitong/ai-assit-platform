package ai.platform.aiassit.db.engine.executor.mysql.provider;

import ai.platform.aiassit.db.engine.executor.mysql.support.MysqlConnectionSupport;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessSourceType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DataSourceCapabilities;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.request.DataReadCommand;
import ai.platform.aiassit.db.engine.executor.spi.request.QueryRequest;
import ai.platform.aiassit.db.engine.executor.spi.result.DataReadResult;
import ai.platform.aiassit.db.engine.executor.spi.result.QueryResult;
import ai.platform.aiassit.db.engine.executor.spi.provider.DataSourceAdapter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * MySQL 的统一数据源读取适配器。
 *
 * <p>仅覆盖通用的资源读取；复杂 SQL、聚合和 DDL 保持在数据库专属 SPI 中，等待语义计划完全迁移。</p>
 */
@Component
public class MysqlDataSourceAdapter implements DataSourceAdapter {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*");

    private final MysqlConnectionSupport connectionSupport;

    public MysqlDataSourceAdapter(MysqlConnectionSupport connectionSupport) {
        this.connectionSupport = connectionSupport;
    }

    @Override
    public DbAccessSourceType sourceType() {
        return DbAccessSourceType.DATABASE;
    }

    @Override
    public boolean supports(DbAccessContext context) {
        return context != null
                && context.getSourceType() == DbAccessSourceType.DATABASE
                && context.getDbType() == DbAccessDbType.MYSQL;
    }

    @Override
    public DataSourceCapabilities capabilities() {
        return DataSourceCapabilities.readOnly();
    }

    @Override
    public DataReadResult read(DbAccessContext context, DataReadCommand command) throws DbAccessException {
        if (context.getDbType() != DbAccessDbType.MYSQL) {
            throw new DbAccessException("MySQL 数据适配器不支持: " + context.getDbType());
        }
        if (command == null || !StringUtils.hasText(command.getResource())) {
            throw new DbAccessException("数据库读取 resource 不能为空");
        }
        String table = quoteIdentifier(requireIdentifier(command.getResource(), "resource"));
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table);
        appendFilters(sql, parameters, command.getParameters());
        Integer pageSize = normalizePageSize(command.getPageSize());
        long offset = (long) (normalizePage(command.getPage()) - 1) * pageSize;
        sql.append(" LIMIT ? OFFSET ?");
        parameters.add(pageSize);
        parameters.add(offset);
        QueryResult result = new MysqlDbAccessExecutor(connectionSupport, context).query(QueryRequest.builder()
                .sql(sql.toString())
                .parameters(parameters)
                .maxRows(pageSize)
                .build());
        return DataReadResult.builder()
                .records(result.getRows() == null ? List.of() : result.getRows())
                .metadata(Map.of("protocol", "MYSQL", "executionMs", result.getExecutionMs()))
                .build();
    }

    private void appendFilters(StringBuilder sql, List<Object> parameters, Map<String, Object> filters) throws DbAccessException {
        if (filters == null || filters.isEmpty()) {
            return;
        }
        List<String> conditions = new ArrayList<>();
        for (Map.Entry<String, Object> entry : new LinkedHashMap<>(filters).entrySet()) {
            String field = quoteIdentifier(requireIdentifier(entry.getKey(), "filter field"));
            if (entry.getValue() == null) {
                conditions.add(field + " IS NULL");
            } else {
                conditions.add(field + " = ?");
                parameters.add(entry.getValue());
            }
        }
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
    }

    private String requireIdentifier(String value, String label) throws DbAccessException {
        if (!StringUtils.hasText(value) || !IDENTIFIER.matcher(value.trim()).matches()) {
            throw new DbAccessException(label + " 不是合法标识符");
        }
        return value.trim();
    }

    private String quoteIdentifier(String identifier) {
        return java.util.Arrays.stream(identifier.split("\\."))
                .map(part -> "`" + part + "`")
                .collect(Collectors.joining("."));
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 100 : Math.min(pageSize, 1_000);
    }
}
