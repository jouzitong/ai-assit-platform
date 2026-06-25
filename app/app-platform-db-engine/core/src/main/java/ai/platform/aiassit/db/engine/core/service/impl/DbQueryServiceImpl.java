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
import ai.platform.aiassit.db.engine.api.dto.DbQueryRelation;
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
    private static final String RELATION_FIELD_ALIAS_PREFIX = "__rel__";
    private static final String RELATION_FIELD_ALIAS_DELIMITER = "__";
    private static final String RELATION_FIELD_PATH_DOT_TOKEN = "__dot__";

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
        List<DbQueryRelation> relations = request == null || request.getExt() == null ? List.of() : request.getExt().getRelations();
        List<String> fields = request == null || request.getExt() == null ? List.of() : request.getExt().getFields();
        List<DbQuerySort> sorts = request == null || request.getExt() == null ? List.of() : request.getExt().getSorts();
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(buildSelectClause(fields, relations))
                .append(" FROM ")
                .append(buildFromClause(table, relations));
        List<String> conditions = new ArrayList<>();
        if (request != null && request.getId() != null) {
            conditions.add(wrapIdentifier("id") + " = " + toSqlLiteral(request.getId()));
        }
        if (request != null) {
            conditions.addAll(buildFilterConditions(request.getFilterDict(), request.getFilterExpr()));
        }
        appendWhere(sql, conditions);
        appendOrderBy(sql, sorts);
        sql.append(" LIMIT 1");
        QueryResult result = runQuery(sql.toString(), 1);
        DbQueryGetResponse response = new DbQueryGetResponse();
        if (!CollectionUtils.isEmpty(result.getRows())) {
            response.setRecord(transformRow(result.getRows().get(0)));
        }
        return response;
    }

    @Override
    public DbQueryListResponse queryList(DbQueryListRequest request) {
        String table = requireModel(request == null ? null : request.getModel());
        List<DbQueryRelation> relations = request == null || request.getExt() == null ? List.of() : request.getExt().getRelations();
        List<String> fields = request == null || request.getExt() == null ? List.of() : request.getExt().getFields();
        int page = normalizePage(request == null ? null : request.getPage());
        int pageSize = normalizePageSize(request == null ? null : request.getPageSize());
        List<String> conditions = buildFilterConditions(
                request == null ? null : request.getFilterDict(),
                request == null ? null : request.getFilterExpr()
        );

        StringBuilder sql = new StringBuilder("SELECT ")
                .append(buildSelectClause(fields, relations))
                .append(" FROM ")
                .append(buildFromClause(table, relations));
        appendWhere(sql, conditions);
        appendOrderBy(sql, request == null || request.getExt() == null ? List.of() : request.getExt().getSorts());
        sql.append(" LIMIT ").append((page - 1) * pageSize).append(", ").append(pageSize);

        QueryResult result = runQuery(sql.toString(), pageSize);
        long total = queryTotal(table, relations, conditions);

        DbQueryListResponse response = new DbQueryListResponse();
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(total);
        response.setRecords(transformRows(result.getRows()));
        return response;
    }

    @Override
    public DbQueryCountResponse queryCount(DbQueryCountRequest request) {
        QueryBundle bundle = buildAggregateBundle(
                requireModel(request == null ? null : request.getModel()),
                request == null || request.getExt() == null ? List.of() : request.getExt().getRelations(),
                request == null ? null : request.getFilterDict(),
                request == null ? null : request.getFilterExpr(),
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
                request == null || request.getExt() == null ? List.of() : request.getExt().getRelations(),
                request == null ? null : request.getFilterDict(),
                request == null ? null : request.getFilterExpr(),
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
        List<DbQueryRelation> relations = request == null || request.getExt() == null ? List.of() : request.getExt().getRelations();
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
                .append(buildSelectClause(new ArrayList<>(fields), relations))
                .append(" FROM ")
                .append(buildFromClause(table, relations));
        appendWhere(sql, buildFilterConditions(
                request == null ? null : request.getFilterDict(),
                request == null ? null : request.getFilterExpr()
        ));
        appendOrderBy(sql, request == null ? null : request.getSorts());

        QueryResult result = runQuery(sql.toString(), DEFAULT_MAX_ROWS);
        List<Map<String, Object>> rows = result.getRows();
        Map<Object, DbQueryTreeNode> nodeMap = new LinkedHashMap<>();
        List<DbQueryTreeNode> roots = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            DbQueryTreeNode node = new DbQueryTreeNode();
            Object id = resolveProjectedValue(row, idField, relations);
            Object parentId = resolveProjectedValue(row, parentField, relations);
            node.setId(id);
            node.setParentId(parentId);
            Object label = resolveProjectedValue(row, labelField, relations);
            node.setLabel(label == null ? null : String.valueOf(label));
            node.setData(transformRow(row));
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
                request != null && request.getExt() != null ? request.getExt().getRelations() : List.of(),
                request == null ? null : request.getFilterDict(),
                request == null ? null : request.getFilterExpr(),
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
            List<DbQueryRelation> relations,
            Map<String, ?> filterDict,
            String filterExpr,
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
        List<String> whereConditions = buildFilterConditions(filterDict, filterExpr);
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(String.join(", ", selectItems))
                .append(" FROM ")
                .append(buildFromClause(table, relations));
        appendWhere(sql, whereConditions);
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

    private long queryTotal(String table, List<DbQueryRelation> relations, List<String> conditions) {
        StringBuilder countSql = new StringBuilder("SELECT COUNT(1) AS cnt FROM ").append(buildFromClause(table, relations));
        appendWhere(countSql, conditions);
        QueryResult result = runQuery(countSql.toString(), 1);
        if (CollectionUtils.isEmpty(result.getRows())) {
            return 0L;
        }
        return toLong(result.getRows().get(0).get("cnt"));
    }

    private String buildFromClause(String table, List<DbQueryRelation> relations) {
        StringBuilder fromClause = new StringBuilder(wrapIdentifier(table));
        if (CollectionUtils.isEmpty(relations)) {
            return fromClause.toString();
        }
        for (DbQueryRelation relation : relations) {
            fromClause.append(" ")
                    .append(resolveJoinType(relation == null ? null : relation.getType()))
                    .append(" ")
                    .append(wrapIdentifier(requireModel(relation == null ? null : relation.getModel())))
                    .append(" ")
                    .append(wrapIdentifier(requireRelationKey(relation)))
                    .append(" ON ")
                    .append(buildJoinConditionSql(relation));
        }
        return fromClause.toString();
    }

    private String buildJoinConditionSql(DbQueryRelation relation) {
        String relationKey = requireRelationKey(relation);
        Map<String, String> on = relation == null ? null : relation.getOn();
        if (CollectionUtils.isEmpty(on)) {
            throw BizException.of();
        }
        List<String> conditions = new ArrayList<>();
        for (Map.Entry<String, String> entry : on.entrySet()) {
            String leftField = wrapIdentifier(requireIdentifier(entry.getKey(), "relation on left field"));
            String rightField = wrapIdentifier(qualifyRelationField(relationKey, entry.getValue(), "relation on right field"));
            conditions.add(leftField + " = " + rightField);
        }
        Map<String, Object> filter = relation == null ? null : relation.getFilter();
        if (!CollectionUtils.isEmpty(filter)) {
            for (Map.Entry<String, Object> entry : filter.entrySet()) {
                conditions.add(buildRelationFilterCondition(relationKey, entry.getKey(), entry.getValue()));
            }
        }
        return String.join(" AND ", conditions);
    }

    private String buildRelationFilterCondition(String relationKey, String field, Object value) {
        String qualifiedField = wrapIdentifier(qualifyRelationField(relationKey, field, "relation filter field"));
        if (value == null) {
            return qualifiedField + " IS NULL";
        }
        if (value instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                throw BizException.of();
            }
            return qualifiedField + " IN (" + toSqlLiteralList(collection) + ")";
        }
        return qualifiedField + " = " + toSqlLiteral(value);
    }

    private QueryResult runQuery(String sql, Integer maxRows) {
        QueryRequest request = QueryRequest.builder()
                .sql(sql)
                .maxRows(maxRows == null ? DEFAULT_MAX_ROWS : maxRows)
                .build();
        return dbAccessService.query(defaultDbSourceKeyResolver.resolve(null), request);
    }

    private String buildSelectClause(List<String> fields, List<DbQueryRelation> relations) {
        if (CollectionUtils.isEmpty(fields)) {
            return "*";
        }
        Set<String> relationKeys = resolveRelationKeys(relations);
        return fields.stream()
                .map(field -> buildSelectItem(field, relationKeys))
                .collect(Collectors.joining(", "));
    }

    private String buildSelectItem(String field, Set<String> relationKeys) {
        String identifier = requireIdentifier(field, "field");
        String wrapped = wrapIdentifier(identifier);
        String relationKey = extractRelationKey(identifier, relationKeys);
        if (relationKey == null) {
            return wrapped;
        }
        return wrapped + " AS " + wrapIdentifier(encodeRelationFieldAlias(relationKey, relationFieldPath(identifier)));
    }

    private List<String> buildWhereConditions(Map<String, ?> filters) {
        List<String> conditions = new ArrayList<>();
        if (filters == null || filters.isEmpty()) {
            return conditions;
        }
        for (Map.Entry<String, ?> entry : filters.entrySet()) {
            String field = wrapIdentifier(requireIdentifier(entry.getKey(), "filter field"));
            DbQueryFilterCondition condition = normalizeFilterCondition(entry.getValue());
            String op = condition == null || !StringUtils.hasText(condition.getOp()) ? "eq" : condition.getOp().trim().toLowerCase(Locale.ROOT);
            Object value = condition == null ? null : condition.getValue();
            conditions.add(buildAtomicCondition(field, op, value));
        }
        return conditions;
    }

    private List<String> buildFilterConditions(Map<String, ?> filterDict, String filterExpr) {
        if (filterDict == null || filterDict.isEmpty()) {
            if (StringUtils.hasText(filterExpr)) {
                throw BizException.of();
            }
            return List.of();
        }
        if (!StringUtils.hasText(filterExpr)) {
            return buildWhereConditions(filterDict);
        }
        Map<String, String> conditionSqlMap = buildConditionSqlMap(filterDict);
        FilterExprParser parser = new FilterExprParser(filterExpr, conditionSqlMap);
        String combinedCondition = parser.parse();
        if (!conditionSqlMap.keySet().equals(parser.getReferencedIdentifiers())) {
            throw BizException.of();
        }
        return List.of(combinedCondition);
    }

    private Map<String, String> buildConditionSqlMap(Map<String, ?> filterDict) {
        Map<String, String> conditionSqlMap = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : filterDict.entrySet()) {
            String key = requireIdentifier(entry.getKey(), "filter key");
            DbQueryFilterCondition condition = normalizeFilterCondition(entry.getValue());
            String op = condition == null || !StringUtils.hasText(condition.getOp()) ? "eq" : condition.getOp().trim().toLowerCase(Locale.ROOT);
            Object value = condition == null ? null : condition.getValue();
            conditionSqlMap.put(key, buildAtomicCondition(wrapIdentifier(key), op, value));
        }
        return conditionSqlMap;
    }

    private String buildAtomicCondition(String field, String op, Object value) {
        switch (op) {
            case "eq":
                return field + " = " + toSqlLiteral(value);
            case "ne":
            case "neq":
                return field + " <> " + toSqlLiteral(value);
            case "gt":
                return field + " > " + toSqlLiteral(value);
            case "gte":
            case "ge":
                return field + " >= " + toSqlLiteral(value);
            case "lt":
                return field + " < " + toSqlLiteral(value);
            case "lte":
            case "le":
                return field + " <= " + toSqlLiteral(value);
            case "like":
                return field + " LIKE " + toSqlLiteral("%" + value + "%");
            case "prefix_like":
                return field + " LIKE " + toSqlLiteral(value + "%");
            case "suffix_like":
                return field + " LIKE " + toSqlLiteral("%" + value);
            case "in":
                return field + " IN (" + toSqlLiteralList(value) + ")";
            case "not_in":
                return field + " NOT IN (" + toSqlLiteralList(value) + ")";
            case "is_null":
                return field + " IS NULL";
            case "is_not_null":
                return field + " IS NOT NULL";
            default:
                throw BizException.of();
        }
    }

    private DbQueryFilterCondition normalizeFilterCondition(Object rawCondition) {
        if (rawCondition == null || rawCondition instanceof DbQueryFilterCondition) {
            return (DbQueryFilterCondition) rawCondition;
        }
        if (rawCondition instanceof Map<?, ?> map) {
            boolean hasOp = map.containsKey("op");
            boolean hasValue = map.containsKey("value");
            if (hasOp || hasValue) {
                DbQueryFilterCondition condition = new DbQueryFilterCondition();
                Object op = map.get("op");
                if (op != null) {
                    condition.setOp(String.valueOf(op));
                }
                condition.setValue(map.get("value"));
                return condition;
            }
        }
        DbQueryFilterCondition condition = new DbQueryFilterCondition();
        condition.setOp("eq");
        condition.setValue(rawCondition);
        return condition;
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
        if (!trimmed.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")) {
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

    private String requireRelationKey(DbQueryRelation relation) {
        return requireIdentifier(relation == null ? null : relation.getKey(), "relation key");
    }

    private Set<String> resolveRelationKeys(List<DbQueryRelation> relations) {
        if (CollectionUtils.isEmpty(relations)) {
            return Set.of();
        }
        Set<String> relationKeys = new LinkedHashSet<>();
        for (DbQueryRelation relation : relations) {
            String relationKey = requireRelationKey(relation);
            if (!relationKeys.add(relationKey)) {
                throw BizException.of();
            }
        }
        return relationKeys;
    }

    private String extractRelationKey(String identifier, Set<String> relationKeys) {
        int separatorIndex = identifier.indexOf('.');
        if (separatorIndex < 0) {
            return null;
        }
        String relationKey = identifier.substring(0, separatorIndex);
        return relationKeys.contains(relationKey) ? relationKey : null;
    }

    private String relationFieldPath(String identifier) {
        int separatorIndex = identifier.indexOf('.');
        if (separatorIndex < 0 || separatorIndex == identifier.length() - 1) {
            throw BizException.of();
        }
        return identifier.substring(separatorIndex + 1);
    }

    private String encodeRelationFieldAlias(String relationKey, String fieldPath) {
        return RELATION_FIELD_ALIAS_PREFIX
                + relationKey
                + RELATION_FIELD_ALIAS_DELIMITER
                + fieldPath.replace(".", RELATION_FIELD_PATH_DOT_TOKEN);
    }

    private Map<String, Object> transformRow(Map<String, Object> row) {
        Map<String, Object> transformed = new LinkedHashMap<>();
        if (row == null || row.isEmpty()) {
            return transformed;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            DecodedRelationField decoded = decodeRelationFieldAlias(entry.getKey());
            if (decoded == null) {
                transformed.put(entry.getKey(), entry.getValue());
                continue;
            }
            Map<String, Object> nested = ensureNestedMap(transformed, decoded.relationKey());
            putNestedValue(nested, decoded.fieldPath(), entry.getValue());
        }
        collapseNullRelationMaps(transformed);
        return transformed;
    }

    private List<Map<String, Object>> transformRows(List<Map<String, Object>> rows) {
        if (CollectionUtils.isEmpty(rows)) {
            return new ArrayList<>();
        }
        return rows.stream()
                .map(this::transformRow)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Object resolveProjectedValue(Map<String, Object> row, String field, List<DbQueryRelation> relations) {
        if (row == null || !StringUtils.hasText(field)) {
            return null;
        }
        String identifier = requireIdentifier(field, "projected field");
        String relationKey = extractRelationKey(identifier, resolveRelationKeys(relations));
        if (relationKey == null) {
            return row.get(identifier);
        }
        return row.get(encodeRelationFieldAlias(relationKey, relationFieldPath(identifier)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> ensureNestedMap(Map<String, Object> target, String key) {
        Object current = target.get(key);
        if (current instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        Map<String, Object> nested = new LinkedHashMap<>();
        target.put(key, nested);
        return nested;
    }

    private void putNestedValue(Map<String, Object> target, String fieldPath, Object value) {
        String[] parts = fieldPath.split("\\.");
        Map<String, Object> current = target;
        for (int i = 0; i < parts.length - 1; i++) {
            current = ensureNestedMap(current, parts[i]);
        }
        current.put(parts[parts.length - 1], value);
    }

    private void collapseNullRelationMaps(Map<String, Object> row) {
        List<String> keysToNull = new ArrayList<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) map;
                collapseNullRelationMaps(nested);
                if (isAllNullValues(nested)) {
                    keysToNull.add(entry.getKey());
                }
            }
        }
        for (String key : keysToNull) {
            row.put(key, null);
        }
    }

    private boolean isAllNullValues(Map<String, Object> map) {
        if (map.isEmpty()) {
            return true;
        }
        for (Object value : map.values()) {
            if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                if (!isAllNullValues(nestedMap)) {
                    return false;
                }
                continue;
            }
            if (value != null) {
                return false;
            }
        }
        return true;
    }

    private DecodedRelationField decodeRelationFieldAlias(String alias) {
        if (!StringUtils.hasText(alias) || !alias.startsWith(RELATION_FIELD_ALIAS_PREFIX)) {
            return null;
        }
        String body = alias.substring(RELATION_FIELD_ALIAS_PREFIX.length());
        int delimiterIndex = body.indexOf(RELATION_FIELD_ALIAS_DELIMITER);
        if (delimiterIndex < 0) {
            return null;
        }
        String relationKey = body.substring(0, delimiterIndex);
        String fieldPath = body.substring(delimiterIndex + RELATION_FIELD_ALIAS_DELIMITER.length())
                .replace(RELATION_FIELD_PATH_DOT_TOKEN, ".");
        if (!StringUtils.hasText(relationKey) || !StringUtils.hasText(fieldPath)) {
            return null;
        }
        return new DecodedRelationField(relationKey, fieldPath);
    }

    private String qualifyRelationField(String relationKey, String field, String label) {
        String identifier = requireIdentifier(field, label);
        return identifier.contains(".") ? identifier : relationKey + "." + identifier;
    }

    private String resolveJoinType(String joinType) {
        String normalized = valueOrDefault(joinType, "left").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "inner" -> "INNER JOIN";
            case "right" -> "RIGHT JOIN";
            case "full" -> "FULL JOIN";
            case "left" -> "LEFT JOIN";
            default -> throw BizException.of();
        };
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

    private record DecodedRelationField(String relationKey, String fieldPath) {
    }

    private static final class FilterExprParser {

        private final List<String> tokens;
        private final Map<String, String> conditionSqlMap;
        private final Set<String> referencedIdentifiers = new LinkedHashSet<>();
        private int index;

        private FilterExprParser(String filterExpr, Map<String, String> conditionSqlMap) {
            this.tokens = tokenizeFilterExpr(filterExpr);
            this.conditionSqlMap = conditionSqlMap;
        }

        private String parse() {
            String sql = parseOrExpression();
            if (index != tokens.size()) {
                throw BizException.of();
            }
            return sql;
        }

        private Set<String> getReferencedIdentifiers() {
            return referencedIdentifiers;
        }

        private String parseOrExpression() {
            String left = parseAndExpression();
            while (matchKeyword("or")) {
                String right = parseAndExpression();
                left = "(" + left + " OR " + right + ")";
            }
            return left;
        }

        private String parseAndExpression() {
            String left = parsePrimaryExpression();
            while (matchKeyword("and")) {
                String right = parsePrimaryExpression();
                left = "(" + left + " AND " + right + ")";
            }
            return left;
        }

        private String parsePrimaryExpression() {
            if (matchToken("(")) {
                String nested = parseOrExpression();
                if (!matchToken(")")) {
                    throw BizException.of();
                }
                return "(" + nested + ")";
            }
            String identifier = consumeIdentifier();
            referencedIdentifiers.add(identifier);
            String conditionSql = conditionSqlMap.get(identifier);
            if (!StringUtils.hasText(conditionSql)) {
                throw BizException.of();
            }
            return conditionSql;
        }

        private boolean matchKeyword(String keyword) {
            if (index >= tokens.size()) {
                return false;
            }
            if (!keyword.equalsIgnoreCase(tokens.get(index))) {
                return false;
            }
            index++;
            return true;
        }

        private boolean matchToken(String token) {
            if (index >= tokens.size()) {
                return false;
            }
            if (!token.equals(tokens.get(index))) {
                return false;
            }
            index++;
            return true;
        }

        private String consumeIdentifier() {
            if (index >= tokens.size()) {
                throw BizException.of();
            }
            String token = tokens.get(index++);
            if ("(".equals(token) || ")".equals(token) || "and".equalsIgnoreCase(token) || "or".equalsIgnoreCase(token)) {
                throw BizException.of();
            }
            return token;
        }

        private static List<String> tokenizeFilterExpr(String filterExpr) {
            if (!StringUtils.hasText(filterExpr)) {
                throw BizException.of();
            }
            List<String> tokens = new ArrayList<>();
            int position = 0;
            while (position < filterExpr.length()) {
                char current = filterExpr.charAt(position);
                if (Character.isWhitespace(current)) {
                    position++;
                    continue;
                }
                if (current == '(' || current == ')') {
                    tokens.add(String.valueOf(current));
                    position++;
                    continue;
                }
                int start = position;
                while (position < filterExpr.length()) {
                    char ch = filterExpr.charAt(position);
                    if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '.') {
                        position++;
                        continue;
                    }
                    break;
                }
                if (start == position) {
                    throw BizException.of();
                }
                tokens.add(filterExpr.substring(start, position));
            }
            return tokens;
        }
    }
}
