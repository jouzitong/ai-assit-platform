package ai.platform.aiassit.db.engine.core.service.impl;

import ai.platform.aiassit.db.engine.api.dto.DbQueryAggregateRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryAggregateResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountDimension;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountMetric;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryFilterCondition;
import ai.platform.aiassit.db.engine.api.dto.DbQueryGetRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryGetResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryListRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryListResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQueryPivotRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryPivotResponse;
import ai.platform.aiassit.db.engine.api.dto.DbQuerySort;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeNode;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeResponse;
import ai.platform.aiassit.db.engine.core.service.DbAccessService;
import ai.platform.aiassit.db.engine.core.service.DbQueryService;
import ai.platform.aiassit.db.engine.core.support.DefaultDbSourceKeyResolver;
import ai.platform.aiassit.db.engine.executor.spi.request.QueryRequest;
import ai.platform.aiassit.db.engine.executor.spi.result.QueryResult;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Service
public class DbQueryServiceImpl implements DbQueryService {

    private static final int DEFAULT_MAX_ROWS = 1000;

    private final DbAccessService dbAccessService;
    private final DefaultDbSourceKeyResolver defaultDbSourceKeyResolver;

    public DbQueryServiceImpl(
            DbAccessService dbAccessService,
            DefaultDbSourceKeyResolver defaultDbSourceKeyResolver
    ) {
        this.dbAccessService = dbAccessService;
        this.defaultDbSourceKeyResolver = defaultDbSourceKeyResolver;
    }

    @Override
    public DbQueryGetResponse queryGet(DbQueryGetRequest request) {
        String table = requireModel(request == null ? null : request.getModel());
        List<String> fields = request == null || request.getExt() == null ? List.of() : request.getExt().getFields();
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(buildSelectClause(fields))
                .append(" FROM ")
                .append(wrapIdentifier(table));
        List<String> conditions = new ArrayList<>();
        if (request != null && request.getId() != null) {
            conditions.add(wrapIdentifier("id") + " = " + toSqlLiteral(request.getId()));
        }
        if (request != null) {
            conditions.addAll(buildWhereConditions(request.getFilters()));
        }
        appendWhere(sql, conditions);
        sql.append(" LIMIT 1");
        QueryResult result = runQuery(sql.toString(), 1);
        DbQueryGetResponse response = new DbQueryGetResponse();
        if (!CollectionUtils.isEmpty(result.getRows())) {
            response.setRecord(new LinkedHashMap<>(result.getRows().get(0)));
        }
        return response;
    }

    @Override
    public DbQueryListResponse queryList(DbQueryListRequest request) {
        String table = requireModel(request == null ? null : request.getModel());
        List<String> fields = request == null || request.getExt() == null ? List.of() : request.getExt().getFields();
        int page = normalizePage(request == null ? null : request.getPage());
        int pageSize = normalizePageSize(request == null ? null : request.getPageSize());
        List<String> conditions = buildWhereConditions(request == null ? null : request.getFilterDict());

        StringBuilder sql = new StringBuilder("SELECT ")
                .append(buildSelectClause(fields))
                .append(" FROM ")
                .append(wrapIdentifier(table));
        appendWhere(sql, conditions);
        appendOrderBy(sql, request == null || request.getExt() == null ? List.of() : request.getExt().getSorts());
        sql.append(" LIMIT ").append((page - 1) * pageSize).append(", ").append(pageSize);

        QueryResult result = runQuery(sql.toString(), pageSize);
        long total = queryTotal(table, conditions);

        DbQueryListResponse response = new DbQueryListResponse();
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(total);
        response.setRecords(copyRows(result.getRows()));
        return response;
    }

    @Override
    public DbQueryCountResponse queryCount(DbQueryCountRequest request) {
        QueryBundle bundle = buildAggregateBundle(
                requireModel(request == null ? null : request.getModel()),
                request == null ? null : request.getFilters(),
                request == null ? null : request.getDimensions(),
                request == null ? null : request.getMetrics(),
                request == null ? null : request.getHaving(),
                request == null ? null : request.getSorts(),
                request == null ? null : request.getPage(),
                request == null ? null : request.getPageSize()
        );
        QueryResult result = runQuery(bundle.sql(), bundle.maxRows());
        DbQueryCountResponse response = new DbQueryCountResponse();
        response.setPage(bundle.page());
        response.setPageSize(bundle.pageSize());
        response.setRecords(copyRows(result.getRows()));
        response.setTotal(resolveAggregateTotal(bundle, result.getRows()));
        response.setSummary(resolveSummaryRow(result.getRows()));
        return response;
    }

    @Override
    public DbQueryAggregateResponse queryAggregate(DbQueryAggregateRequest request) {
        QueryBundle bundle = buildAggregateBundle(
                requireModel(request == null ? null : request.getModel()),
                request == null ? null : request.getFilters(),
                request == null ? null : request.getDimensions(),
                request == null ? null : request.getMetrics(),
                request == null ? null : request.getHaving(),
                request == null ? null : request.getSorts(),
                request == null ? null : request.getPage(),
                request == null ? null : request.getPageSize()
        );
        QueryResult result = runQuery(bundle.sql(), bundle.maxRows());
        DbQueryAggregateResponse response = new DbQueryAggregateResponse();
        response.setPage(bundle.page());
        response.setPageSize(bundle.pageSize());
        response.setRecords(copyRows(result.getRows()));
        response.setTotal(resolveAggregateTotal(bundle, result.getRows()));
        response.setSummary(resolveSummaryRow(result.getRows()));
        return response;
    }

    @Override
    public DbQueryTreeResponse queryTree(DbQueryTreeRequest request) {
        String table = requireModel(request == null ? null : request.getModel());
        String idField = valueOrDefault(request == null || request.getExt() == null ? null : request.getExt().getIdField(), "id");
        String parentField = valueOrDefault(request == null || request.getExt() == null ? null : request.getExt().getParentField(), "parent_id");
        String labelField = valueOrDefault(request == null || request.getExt() == null ? null : request.getExt().getLabelField(), "name");

        Set<String> fields = new LinkedHashSet<>();
        fields.add(idField);
        fields.add(parentField);
        fields.add(labelField);
        if (request != null && request.getFields() != null) {
            fields.addAll(request.getFields());
        }

        StringBuilder sql = new StringBuilder("SELECT ")
                .append(buildSelectClause(new ArrayList<>(fields)))
                .append(" FROM ")
                .append(wrapIdentifier(table));
        appendWhere(sql, buildWhereConditions(request == null ? null : request.getFilters()));
        appendOrderBy(sql, request == null ? null : request.getSorts());

        QueryResult result = runQuery(sql.toString(), DEFAULT_MAX_ROWS);
        List<Map<String, Object>> rows = result.getRows();
        Map<Object, DbQueryTreeNode> nodeMap = new LinkedHashMap<>();
        List<DbQueryTreeNode> roots = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            DbQueryTreeNode node = new DbQueryTreeNode();
            Object id = row.get(idField);
            Object parentId = row.get(parentField);
            node.setId(id);
            node.setParentId(parentId);
            Object label = row.get(labelField);
            node.setLabel(label == null ? null : String.valueOf(label));
            node.setData(new LinkedHashMap<>(row));
            nodeMap.put(id, node);
        }
        Object rootValue = request == null || request.getExt() == null ? null : request.getExt().getRootValue();
        for (DbQueryTreeNode node : nodeMap.values()) {
            if (isRootNode(node.getParentId(), rootValue) || !nodeMap.containsKey(node.getParentId())) {
                roots.add(node);
            } else {
                nodeMap.get(node.getParentId()).getChildren().add(node);
            }
        }
        DbQueryTreeResponse response = new DbQueryTreeResponse();
        response.setRecords(roots);
        return response;
    }

    @Override
    public DbQueryPivotResponse queryPivot(DbQueryPivotRequest request) {
        String table = requireModel(request == null ? null : request.getModel());
        List<DbQueryCountDimension> rows = request == null ? List.of() : request.getRows();
        List<DbQueryCountDimension> columns = request == null ? List.of() : request.getColumns();
        List<DbQueryCountMetric> metrics = request == null ? List.of() : request.getMetrics();
        if (CollectionUtils.isEmpty(rows) || CollectionUtils.isEmpty(columns) || CollectionUtils.isEmpty(metrics)) {
            throw BizException.of();
        }
        List<DbQueryCountDimension> dimensions = new ArrayList<>();
        dimensions.addAll(rows);
        dimensions.addAll(columns);
        QueryBundle bundle = buildAggregateBundle(
                table,
                request == null ? null : request.getFilters(),
                dimensions,
                metrics,
                request == null ? null : request.getHaving(),
                List.of(),
                1,
                request != null && request.getExt() != null ? request.getExt().getTopN() : null
        );
        List<Map<String, Object>> records = copyRows(runQuery(bundle.sql(), bundle.maxRows()).getRows());

        List<String> rowKeys = rows.stream().map(this::resolveDimensionAlias).toList();
        List<String> columnKeys = columns.stream().map(this::resolveDimensionAlias).toList();
        List<String> metricKeys = metrics.stream().map(this::resolveMetricAlias).toList();

        Map<String, Map<String, Object>> pivotRowMap = new LinkedHashMap<>();
        LinkedHashSet<String> pivotColumns = new LinkedHashSet<>();
        Object fillValue = request != null && request.getExt() != null ? request.getExt().getFillValue() : null;

        for (Map<String, Object> record : records) {
            String rowKey = joinKey(record, rowKeys);
            Map<String, Object> pivotRow = pivotRowMap.computeIfAbsent(rowKey, key -> {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String field : rowKeys) {
                    row.put(field, record.get(field));
                }
                return row;
            });
            String columnKey = joinKey(record, columnKeys);
            for (String metricKey : metricKeys) {
                String pivotColumn = metricKeys.size() == 1 ? columnKey : columnKey + ":" + metricKey;
                pivotColumns.add(pivotColumn);
                pivotRow.put(pivotColumn, record.get(metricKey));
            }
        }
        List<Map<String, Object>> pivotRecords = new ArrayList<>(pivotRowMap.values());
        for (Map<String, Object> row : pivotRecords) {
            for (String columnKey : pivotColumns) {
                row.putIfAbsent(columnKey, fillValue);
            }
        }

        DbQueryPivotResponse response = new DbQueryPivotResponse();
        response.setColumnKeys(new ArrayList<>(pivotColumns));
        response.setRecords(pivotRecords);
        response.setSummary(resolveSummaryRow(records));
        return response;
    }

    private QueryBundle buildAggregateBundle(
            String table,
            Map<String, DbQueryFilterCondition> filters,
            List<DbQueryCountDimension> dimensions,
            List<DbQueryCountMetric> metrics,
            Map<String, DbQueryFilterCondition> having,
            List<DbQuerySort> sorts,
            Integer page,
            Integer pageSize
    ) {
        List<String> selectItems = new ArrayList<>();
        List<String> groupByItems = new ArrayList<>();
        if (!CollectionUtils.isEmpty(dimensions)) {
            for (DbQueryCountDimension dimension : dimensions) {
                String field = requireIdentifier(dimension.getField(), "dimension field");
                String alias = resolveDimensionAlias(dimension);
                selectItems.add(wrapIdentifier(field) + " AS " + wrapIdentifier(alias));
                groupByItems.add(wrapIdentifier(field));
            }
        }
        if (CollectionUtils.isEmpty(metrics)) {
            selectItems.add("COUNT(1) AS " + wrapIdentifier("count"));
        } else {
            for (DbQueryCountMetric metric : metrics) {
                selectItems.add(buildMetricSql(metric));
            }
        }

        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(String.join(", ", selectItems))
                .append(" FROM ")
                .append(wrapIdentifier(table));
        appendWhere(sql, buildWhereConditions(filters));
        if (!groupByItems.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", groupByItems));
        }
        appendHaving(sql, buildWhereConditions(having));
        appendOrderBy(sql, sorts);
        sql.append(" LIMIT ").append((safePage - 1) * safePageSize).append(", ").append(safePageSize);
        return new QueryBundle(sql.toString(), safePageSize, safePage, safePageSize, !groupByItems.isEmpty());
    }

    private String buildMetricSql(DbQueryCountMetric metric) {
        String func = valueOrDefault(metric == null ? null : metric.getFunc(), "count").toUpperCase(Locale.ROOT);
        String alias = resolveMetricAlias(metric);
        String field = metric == null ? null : metric.getField();
        String target = "COUNT".equals(func) && !StringUtils.hasText(field) ? "1" : wrapIdentifier(requireIdentifier(field, "metric field"));
        return func + "(" + target + ") AS " + wrapIdentifier(alias);
    }

    private long queryTotal(String table, List<String> conditions) {
        StringBuilder countSql = new StringBuilder("SELECT COUNT(1) AS cnt FROM ").append(wrapIdentifier(table));
        appendWhere(countSql, conditions);
        QueryResult result = runQuery(countSql.toString(), 1);
        if (CollectionUtils.isEmpty(result.getRows())) {
            return 0L;
        }
        return toLong(result.getRows().get(0).get("cnt"));
    }

    private QueryResult runQuery(String sql, Integer maxRows) {
        QueryRequest request = QueryRequest.builder()
                .sql(sql)
                .maxRows(maxRows == null ? DEFAULT_MAX_ROWS : maxRows)
                .build();
        return dbAccessService.query(defaultDbSourceKeyResolver.resolve(null), request);
    }

    private String buildSelectClause(List<String> fields) {
        if (CollectionUtils.isEmpty(fields)) {
            return "*";
        }
        return fields.stream()
                .map(field -> wrapIdentifier(requireIdentifier(field, "field")))
                .collect(Collectors.joining(", "));
    }

    private List<String> buildWhereConditions(Map<String, DbQueryFilterCondition> filters) {
        List<String> conditions = new ArrayList<>();
        if (filters == null || filters.isEmpty()) {
            return conditions;
        }
        for (Map.Entry<String, DbQueryFilterCondition> entry : filters.entrySet()) {
            String field = wrapIdentifier(requireIdentifier(entry.getKey(), "filter field"));
            DbQueryFilterCondition condition = entry.getValue();
            String op = condition == null || !StringUtils.hasText(condition.getOp()) ? "eq" : condition.getOp().trim().toLowerCase(Locale.ROOT);
            Object value = condition == null ? null : condition.getValue();
            switch (op) {
                case "eq" -> conditions.add(field + " = " + toSqlLiteral(value));
                case "ne", "neq" -> conditions.add(field + " <> " + toSqlLiteral(value));
                case "gt" -> conditions.add(field + " > " + toSqlLiteral(value));
                case "gte", "ge" -> conditions.add(field + " >= " + toSqlLiteral(value));
                case "lt" -> conditions.add(field + " < " + toSqlLiteral(value));
                case "lte", "le" -> conditions.add(field + " <= " + toSqlLiteral(value));
                case "like" -> conditions.add(field + " LIKE " + toSqlLiteral("%" + value + "%"));
                case "prefix_like" -> conditions.add(field + " LIKE " + toSqlLiteral(value + "%"));
                case "suffix_like" -> conditions.add(field + " LIKE " + toSqlLiteral("%" + value));
                case "in" -> conditions.add(field + " IN (" + toSqlLiteralList(value) + ")");
                case "not_in" -> conditions.add(field + " NOT IN (" + toSqlLiteralList(value) + ")");
                case "is_null" -> conditions.add(field + " IS NULL");
                case "is_not_null" -> conditions.add(field + " IS NOT NULL");
                default -> throw BizException.of();
            }
        }
        return conditions;
    }

    private void appendWhere(StringBuilder sql, List<String> conditions) {
        if (!CollectionUtils.isEmpty(conditions)) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
    }

    private void appendHaving(StringBuilder sql, List<String> conditions) {
        if (!CollectionUtils.isEmpty(conditions)) {
            sql.append(" HAVING ").append(String.join(" AND ", conditions));
        }
    }

    private void appendOrderBy(StringBuilder sql, List<DbQuerySort> sorts) {
        if (CollectionUtils.isEmpty(sorts)) {
            return;
        }
        List<String> items = new ArrayList<>();
        for (DbQuerySort sort : sorts) {
            if (sort == null || !StringUtils.hasText(sort.getField())) {
                continue;
            }
            String direction = "DESC".equalsIgnoreCase(sort.getOrder()) ? "DESC" : "ASC";
            items.add(wrapIdentifier(requireIdentifier(sort.getField(), "sort field")) + " " + direction);
        }
        if (!items.isEmpty()) {
            sql.append(" ORDER BY ").append(String.join(", ", items));
        }
    }

    private String requireModel(String model) {
        return requireIdentifier(model, "model");
    }

    private String requireIdentifier(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw BizException.of();
        }
        String trimmed = value.trim();
        if (!trimmed.matches("[A-Za-z0-9_]+")) {
            throw BizException.of();
        }
        return trimmed;
    }

    private String wrapIdentifier(String identifier) {
        String[] parts = identifier.split("\\.");
        return java.util.Arrays.stream(parts)
                .map(part -> "`" + part + "`")
                .collect(Collectors.joining("."));
    }

    private String toSqlLiteralList(Object value) {
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            throw BizException.of();
        }
        return collection.stream()
                .map(this::toSqlLiteral)
                .collect(Collectors.joining(", "));
    }

    private String toSqlLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        String text = String.valueOf(value).replace("'", "''");
        return "'" + text + "'";
    }

    private List<Map<String, Object>> copyRows(List<Map<String, Object>> rows) {
        if (CollectionUtils.isEmpty(rows)) {
            return new ArrayList<>();
        }
        return rows.stream()
                .map(LinkedHashMap::new)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private long resolveAggregateTotal(QueryBundle bundle, List<Map<String, Object>> rows) {
        if (!bundle.grouped()) {
            if (CollectionUtils.isEmpty(rows)) {
                return 0L;
            }
            Map<String, Object> first = rows.get(0);
            Object count = first.get("count");
            if (count != null) {
                return toLong(count);
            }
        }
        return rows == null ? 0L : rows.size();
    }

    private Map<String, Object> resolveSummaryRow(List<Map<String, Object>> rows) {
        if (CollectionUtils.isEmpty(rows)) {
            return new LinkedHashMap<>();
        }
        if (rows.size() == 1) {
            return new LinkedHashMap<>(rows.get(0));
        }
        return new LinkedHashMap<>();
    }

    private String resolveDimensionAlias(DbQueryCountDimension dimension) {
        if (dimension != null && StringUtils.hasText(dimension.getAlias())) {
            return requireIdentifier(dimension.getAlias(), "dimension alias");
        }
        return requireIdentifier(dimension == null ? null : dimension.getField(), "dimension field");
    }

    private String resolveMetricAlias(DbQueryCountMetric metric) {
        if (metric != null && StringUtils.hasText(metric.getAlias())) {
            return requireIdentifier(metric.getAlias(), "metric alias");
        }
        String func = valueOrDefault(metric == null ? null : metric.getFunc(), "count").toLowerCase(Locale.ROOT);
        String field = metric == null || !StringUtils.hasText(metric.getField()) ? "all" : requireIdentifier(metric.getField(), "metric field");
        return requireIdentifier(func + "_" + field, "metric alias");
    }

    private String joinKey(Map<String, Object> record, List<String> fields) {
        StringJoiner joiner = new StringJoiner("|");
        for (String field : fields) {
            joiner.add(Objects.toString(record.get(field), ""));
        }
        return joiner.toString();
    }

    private boolean isRootNode(Object parentId, Object rootValue) {
        if (rootValue == null) {
            return parentId == null || "".equals(parentId) || "0".equals(String.valueOf(parentId));
        }
        return Objects.equals(parentId, rootValue);
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, DEFAULT_MAX_ROWS);
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String valueOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private record QueryBundle(String sql, int maxRows, int page, int pageSize, boolean grouped) {
    }
}
