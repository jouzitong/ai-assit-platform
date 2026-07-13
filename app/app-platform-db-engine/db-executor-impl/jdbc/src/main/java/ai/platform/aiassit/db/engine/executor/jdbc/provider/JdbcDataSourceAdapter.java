package ai.platform.aiassit.db.engine.executor.jdbc.provider;

import ai.platform.aiassit.db.engine.executor.jdbc.support.JdbcConnectionSupport;
import ai.platform.aiassit.db.engine.executor.jdbc.support.JdbcDatabaseProfile;
import ai.platform.aiassit.db.engine.executor.jdbc.support.JdbcDdlSupport;
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

@Component
public class JdbcDataSourceAdapter implements DataSourceAdapter {

    private final JdbcConnectionSupport connectionSupport;

    public JdbcDataSourceAdapter(JdbcConnectionSupport connectionSupport) {
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
                && JdbcDatabaseProfile.supports(context.getDbType());
    }

    @Override
    public DataSourceCapabilities capabilities() {
        return DataSourceCapabilities.readOnly();
    }

    @Override
    public DataReadResult read(DbAccessContext context, DataReadCommand command) throws DbAccessException {
        if (command == null || !StringUtils.hasText(command.getResource())) {
            throw new DbAccessException("数据库读取 resource 不能为空");
        }
        JdbcDatabaseProfile profile = JdbcDatabaseProfile.require(context.getDbType());
        JdbcDdlSupport ddl = new JdbcDdlSupport(profile.resolveFamily(context));
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(ddl.quoteResource(command.getResource()));
        List<Object> parameters = new ArrayList<>();
        appendFilters(sql, parameters, command.getParameters(), ddl);
        int page = normalizePage(command.getPage());
        int pageSize = normalizePageSize(command.getPageSize());
        long offset = (long) (page - 1) * pageSize;
        sql = new StringBuilder(ddl.pagedQuery(sql.toString()));
        if (profile.resolveFamily(context) == JdbcDatabaseProfile.DdlFamily.ORACLE) {
            parameters.add(offset);
            parameters.add(pageSize);
        } else {
            parameters.add(pageSize);
            parameters.add(offset);
        }
        QueryResult result = new JdbcDbAccessExecutor(connectionSupport, context).query(QueryRequest.builder()
                .sql(sql.toString())
                .parameters(parameters)
                .maxRows(pageSize)
                .build());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("protocol", context.getDbType().name());
        metadata.put("executionMs", result.getExecutionMs());
        return DataReadResult.builder()
                .records(result.getRows() == null ? List.of() : result.getRows())
                .metadata(metadata)
                .build();
    }

    private void appendFilters(
            StringBuilder sql,
            List<Object> parameters,
            Map<String, Object> filters,
            JdbcDdlSupport ddl
    ) throws DbAccessException {
        if (filters == null || filters.isEmpty()) {
            return;
        }
        List<String> conditions = new ArrayList<>();
        for (Map.Entry<String, Object> entry : new LinkedHashMap<>(filters).entrySet()) {
            String field = ddl.quoteResource(entry.getKey());
            if (entry.getValue() == null) {
                conditions.add(field + " IS NULL");
            } else {
                conditions.add(field + " = ?");
                parameters.add(entry.getValue());
            }
        }
        sql.append(" WHERE ").append(String.join(" AND ", conditions));
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 100 : Math.min(pageSize, 1_000);
    }
}
