package ai.platform.aiassit.db.engine.virtualization.adapter.service;

import ai.platform.aiassit.data.virtualization.api.VirtualCatalogGateway;
import ai.platform.aiassit.data.virtualization.api.VirtualQueryGateway;
import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.dto.QueryHints;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualAggregate;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualCatalogDescriptor;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualGroupBy;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualPage;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryResponse;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualSort;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.AggregateFunction;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.QueryType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.SortDirection;
import ai.platform.aiassit.data.virtualization.api.exception.VirtualDataRuntimeException;
import ai.platform.aiassit.db.engine.api.constant.DataPreviewErrorCode;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryRequest;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryResponse;
import org.arthena.framework.common.context.SystemContext;
import org.athena.framework.security.api.model.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 将 Agent DataContract 收口为只读、限量的已发布虚拟查询。 */
@Service
public class DataPreviewApplicationService {

    static final int MAX_PREVIEW_ROWS = 100;
    static final int MAX_PHYSICAL_TASKS = 8;
    static final int MAX_SCAN_ROWS = 5_000;
    static final int TIMEOUT_MS = 10_000;

    private static final int DEFAULT_PREVIEW_ROWS = 20;
    private static final int MAX_MEASURES = 20;
    private static final int MAX_DIMENSIONS = 20;
    private static final int MAX_FILTERS = 50;
    private static final int MAX_SORTS = 10;
    private static final int MAX_FILTER_VALUES = 100;
    private static final int MAX_POLICY_FILTER_DEPTH = 16;
    private static final Pattern MODEL_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]{0,63}(?:\\.[A-Za-z_][A-Za-z0-9_]{0,63})?"
    );
    private static final Pattern ALIAS_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Pattern SOURCE_REVISION_PATTERN = Pattern.compile("^(?:virtual-model/)?v([1-9][0-9]*)$");
    private static final String SOURCE_REVISION_PREFIX = "virtual-model/v";

    private final VirtualCatalogGateway catalogGateway;
    private final VirtualQueryGateway queryGateway;
    private final DataPreviewAccessPolicy accessPolicy;

    public DataPreviewApplicationService(
            VirtualCatalogGateway catalogGateway,
            VirtualQueryGateway queryGateway,
            DataPreviewAccessPolicy accessPolicy
    ) {
        this.catalogGateway = catalogGateway;
        this.queryGateway = queryGateway;
        this.accessPolicy = accessPolicy;
    }

    public DataPreviewQueryResponse query(DataPreviewQueryRequest source) {
        requireRequest(source);
        UserContext userContext = requireAuthenticatedUser();
        String model = requireModel(source.getModel());
        Long requestedVersion = requestedCatalogVersion(source.getSourceRevision(), source.getCatalogVersion());
        VirtualCatalogDescriptor catalog = catalogGateway.describePublished(model, requestedVersion);
        if (catalog == null || !model.equals(catalog.entityCode())) {
            throw error(DataPreviewErrorCode.REQUEST_INVALID, "已发布虚拟目录返回了不匹配的模型");
        }
        if (requestedVersion != null && catalog.catalogVersion() != requestedVersion.longValue()) {
            throw error(
                    DataPreviewErrorCode.SOURCE_REVISION_CONFLICT,
                    "已发布虚拟目录版本与请求 sourceRevision 不一致"
            );
        }

        QueryShape shape = buildShape(source);
        // Keep the shape immutable. Filters and time ranges add fields to the authorization
        // set, so collect those references in a request-local mutable copy instead of
        // mutating the record component returned by buildShape().
        LinkedHashSet<String> referencedFields = new LinkedHashSet<>(shape.referencedFields());
        LinkedHashSet<String> filterFields = new LinkedHashSet<>();
        FilterNode requestFilter = buildRequestFilter(source, filterFields);
        referencedFields.addAll(filterFields);
        PublishedFieldResolution publishedFields = validatePublishedFields(
                catalog,
                referencedFields,
                optionalOutputFields(shape, filterFields)
        );
        List<String> executionFields = shape.queryType() == QueryType.LIST
                ? executableListFields(shape, publishedFields.unknownOutputFields(), catalog)
                : List.of();
        List<VirtualGroupBy> executionGroupings = executableGroupings(
                shape.groupings(), publishedFields.unknownOutputFields());
        List<VirtualAggregate> executionAggregates = executableAggregates(
                shape.aggregates(), publishedFields.unknownOutputFields());
        String fallbackField = fallbackExecutionField(catalog);
        boolean fallbackAggregate = shape.queryType() == QueryType.AGGREGATE
                && executionAggregates.isEmpty()
                && fallbackField != null;
        if (fallbackAggregate) {
            executionAggregates = List.of(fallbackCount(fallbackField));
        }

        LinkedHashSet<String> authorizedFields = new LinkedHashSet<>(publishedFields.knownFields());
        authorizedFields.addAll(executionFields);
        if (fallbackAggregate) {
            authorizedFields.add(fallbackField);
        }

        DataPreviewAccessPolicy.AccessDecision decision = accessPolicy.authorize(
                new DataPreviewAccessPolicy.AccessRequest(userContext, catalog, authorizedFields)
        );
        enforceFieldDecision(authorizedFields, decision);
        validateEnforcedRowFilter(catalog, decision.enforcedRowFilter());

        boolean executable = shape.queryType() == QueryType.LIST
                ? !executionFields.isEmpty()
                : !executionAggregates.isEmpty();
        if (!executable) {
            return response(catalog, shape, new VirtualQueryResponse());
        }

        VirtualQueryRequest target = new VirtualQueryRequest();
        target.setEntityCode(catalog.entityCode());
        target.setCatalogVersion(catalog.catalogVersion());
        target.setQueryType(shape.queryType());
        target.setFields(executionFields);
        target.setGroupings(executionGroupings);
        target.setAggregates(executionAggregates);
        target.setFilter(and(requestFilter, decision.enforcedRowFilter()));
        target.setRelationCodes(publishedFields.relationCodes());
        target.setSorts(buildSorts(
                source.getSorts(),
                shape.sortFields(),
                unknownOutputSortReferences(shape, publishedFields.unknownOutputFields())
        ));
        target.setPage(previewPage(normalizeLimit(source.getLimit())));
        target.setExactTotal(false);
        target.setTraceLabel("agent-data-preview");
        target.setHints(previewHints());

        VirtualQueryResponse result = queryGateway.query(target);
        return response(catalog, shape, result);
    }

    private QueryShape buildShape(DataPreviewQueryRequest source) {
        List<DataPreviewQueryRequest.Measure> measures = safe(source.getMeasures());
        List<DataPreviewQueryRequest.Dimension> dimensions = safe(source.getDimensions());
        requireMaxSize(measures, MAX_MEASURES, "measures");
        requireMaxSize(dimensions, MAX_DIMENSIONS, "dimensions");
        if (measures.isEmpty() && dimensions.isEmpty()) {
            throw error(DataPreviewErrorCode.REQUEST_INVALID, "至少需要一个 measure 或 dimension");
        }
        return measures.isEmpty() ? listShape(dimensions) : aggregateShape(dimensions, measures);
    }

    private QueryShape listShape(List<DataPreviewQueryRequest.Dimension> dimensions) {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        LinkedHashSet<String> referencedFields = new LinkedHashSet<>();
        Map<String, String> sortFields = new LinkedHashMap<>();
        Set<String> outputKeys = new LinkedHashSet<>();
        List<DataPreviewQueryResponse.Column> columns = new ArrayList<>();
        for (DataPreviewQueryRequest.Dimension dimension : dimensions) {
            if (dimension == null) {
                throw error(DataPreviewErrorCode.REQUEST_INVALID, "dimension 不能为空");
            }
            String field = requireField(dimension.getField());
            String key = outputKey(dimension.getAlias(), field.replace('.', '_'));
            requireUniqueOutputKey(outputKeys, key);
            fields.add(field);
            referencedFields.add(field);
            sortFields.put(field, field);
            sortFields.put(key, field);
            columns.add(column(key, field, label(dimension.getLabel(), field), null));
        }
        return new QueryShape(
                QueryType.LIST,
                List.copyOf(fields),
                List.of(),
                List.of(),
                Set.copyOf(referencedFields),
                Map.copyOf(sortFields),
                List.copyOf(columns)
        );
    }

    private QueryShape aggregateShape(
            List<DataPreviewQueryRequest.Dimension> dimensions,
            List<DataPreviewQueryRequest.Measure> measures
    ) {
        List<VirtualGroupBy> groupings = new ArrayList<>();
        List<VirtualAggregate> aggregates = new ArrayList<>();
        LinkedHashSet<String> referencedFields = new LinkedHashSet<>();
        Map<String, String> sortFields = new LinkedHashMap<>();
        Map<String, String> uniqueMeasureFieldAliases = new LinkedHashMap<>();
        Set<String> duplicateMeasureFields = new LinkedHashSet<>();
        Set<String> outputKeys = new LinkedHashSet<>();
        List<DataPreviewQueryResponse.Column> columns = new ArrayList<>();

        for (DataPreviewQueryRequest.Dimension dimension : dimensions) {
            if (dimension == null) {
                throw error(DataPreviewErrorCode.REQUEST_INVALID, "dimension 不能为空");
            }
            String field = requireField(dimension.getField());
            String alias = outputKey(dimension.getAlias(), field.replace('.', '_'));
            requireUniqueOutputKey(outputKeys, alias);
            VirtualGroupBy grouping = new VirtualGroupBy();
            grouping.setField(field);
            grouping.setAlias(alias);
            groupings.add(grouping);
            referencedFields.add(field);
            sortFields.put(field, alias);
            sortFields.put(alias, alias);
            columns.add(column(alias, field, label(dimension.getLabel(), field), null));
        }

        for (DataPreviewQueryRequest.Measure measure : measures) {
            if (measure == null) {
                throw error(DataPreviewErrorCode.AGGREGATE_INVALID, "measure 不能为空");
            }
            String field = requireField(measure.getField());
            AggregateFunction function = aggregateFunction(measure.getAggregation());
            String alias = outputKey(
                    measure.getAlias(),
                    function.name().toLowerCase(Locale.ROOT) + "_" + field.replace('.', '_')
            );
            requireUniqueOutputKey(outputKeys, alias);
            VirtualAggregate aggregate = new VirtualAggregate();
            aggregate.setField(field);
            aggregate.setFunction(function);
            aggregate.setAlias(alias);
            aggregates.add(aggregate);
            referencedFields.add(field);
            sortFields.put(alias, alias);
            if (uniqueMeasureFieldAliases.putIfAbsent(field, alias) != null) {
                duplicateMeasureFields.add(field);
            }
            columns.add(column(alias, field, label(measure.getLabel(), alias), function.name()));
        }
        duplicateMeasureFields.forEach(uniqueMeasureFieldAliases::remove);
        sortFields.putAll(uniqueMeasureFieldAliases);
        return new QueryShape(
                QueryType.AGGREGATE,
                List.of(),
                List.copyOf(groupings),
                List.copyOf(aggregates),
                Set.copyOf(referencedFields),
                Map.copyOf(sortFields),
                List.copyOf(columns)
        );
    }

    private FilterNode buildRequestFilter(
            DataPreviewQueryRequest source,
            Set<String> filterFields
    ) {
        List<DataPreviewQueryRequest.Filter> filters = safe(source.getFilters());
        requireMaxSize(filters, MAX_FILTERS, "filters");
        List<FilterNode> nodes = new ArrayList<>();
        for (DataPreviewQueryRequest.Filter filter : filters) {
            if (filter == null) {
                throw error(DataPreviewErrorCode.FILTER_INVALID, "filter 不能为空");
            }
            String field = requireField(filter.getField());
            filterFields.add(field);
            FilterOperator operator = filterOperator(filter.getOperator());
            validateFilterValues(filter, operator);
            FilterNode node = predicate(field, operator, filter.getValue(), filter.getValues());
            if ((operator == FilterOperator.IN || operator == FilterOperator.NOT_IN)
                    && (node.getValues() == null || node.getValues().isEmpty())) {
                throw error(DataPreviewErrorCode.FILTER_INVALID, operator + " 需要非空 values");
            }
            nodes.add(node);
        }
        appendTimeRange(nodes, source.getTimeRange(), filterFields);
        return and(nodes);
    }

    private void appendTimeRange(
            List<FilterNode> nodes,
            DataPreviewQueryRequest.TimeRange timeRange,
            Set<String> filterFields
    ) {
        if (timeRange == null) {
            return;
        }
        String field = requireField(timeRange.getField());
        filterFields.add(field);
        Range range = resolveRange(timeRange);
        if (range.start() != null) {
            nodes.add(predicate(field, FilterOperator.GTE, range.start(), List.of()));
        }
        if (range.end() != null) {
            nodes.add(predicate(field, FilterOperator.LT, range.end(), List.of()));
        }
        if (range.start() == null && range.end() == null) {
            throw error(DataPreviewErrorCode.TIME_RANGE_INVALID, "timeRange 必须提供 preset、start 或 end");
        }
        validateRangeOrder(range.start(), range.end());
    }

    private Range resolveRange(DataPreviewQueryRequest.TimeRange source) {
        Object start = source.getStart();
        Object end = source.getEnd();
        validateTimeBoundary(start, "timeRange.start");
        validateTimeBoundary(end, "timeRange.end");
        if (StringUtils.hasText(source.getPreset())) {
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
            Range preset = switch (source.getPreset().trim().toUpperCase(Locale.ROOT)) {
                case "LAST_7_DAYS" -> new Range(now.minusDays(7).toInstant(), now.toInstant());
                case "LAST_30_DAYS" -> new Range(now.minusDays(30).toInstant(), now.toInstant());
                case "LAST_3_MONTHS" -> new Range(now.minusMonths(3).toInstant(), now.toInstant());
                case "LAST_6_MONTHS" -> new Range(now.minusMonths(6).toInstant(), now.toInstant());
                case "LAST_12_MONTHS" -> new Range(now.minusMonths(12).toInstant(), now.toInstant());
                case "THIS_MONTH" -> {
                    ZonedDateTime first = now.withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC);
                    yield new Range(first.toInstant(), first.plusMonths(1).toInstant());
                }
                case "THIS_YEAR" -> {
                    ZonedDateTime first = now.withDayOfYear(1).toLocalDate().atStartOfDay(ZoneOffset.UTC);
                    yield new Range(first.toInstant(), first.plusYears(1).toInstant());
                }
                default -> throw error(
                        DataPreviewErrorCode.TIME_RANGE_INVALID,
                        "不支持的 timeRange preset: " + source.getPreset()
                );
            };
            if (start == null) {
                start = preset.start();
            }
            if (end == null) {
                end = preset.end();
            }
        }
        return new Range(start, end);
    }

    private PublishedFieldResolution validatePublishedFields(
            VirtualCatalogDescriptor catalog,
            Set<String> requestedFields,
            Set<String> optionalOutputFields
    ) {
        // 仅用于输出的未知字段允许软失败；过滤、时间范围等查询语义字段不会进入
        // optionalOutputFields，仍必须通过已发布目录校验。
        Set<String> localFields = enabledFields(catalog);
        Map<String, List<VirtualCatalogDescriptor.Relation>> relationsByCode = new LinkedHashMap<>();
        for (VirtualCatalogDescriptor.Relation relation : catalog.relations()) {
            relationsByCode.computeIfAbsent(relation.code(), ignored -> new ArrayList<>()).add(relation);
        }
        LinkedHashSet<String> relationCodes = new LinkedHashSet<>();
        LinkedHashSet<String> knownFields = new LinkedHashSet<>();
        LinkedHashSet<String> unknownOutputFields = new LinkedHashSet<>();
        Map<String, Set<String>> remoteFields = new LinkedHashMap<>();
        for (String field : requestedFields) {
            boolean optional = optionalOutputFields.contains(field);
            int separator = field.indexOf('.');
            if (separator < 0) {
                if (!localFields.contains(field)) {
                    if (optional) {
                        unknownOutputFields.add(field);
                        continue;
                    }
                    throw error(DataPreviewErrorCode.FIELD_NOT_FOUND, "虚拟字段不存在或未启用: " + field);
                }
                knownFields.add(field);
                continue;
            }
            String relationCode = field.substring(0, separator);
            String remoteField = field.substring(separator + 1);
            List<VirtualCatalogDescriptor.Relation> candidates = relationsByCode.getOrDefault(relationCode, List.of());
            if (candidates.isEmpty()) {
                if (optional) {
                    unknownOutputFields.add(field);
                    continue;
                }
                throw error(DataPreviewErrorCode.RELATION_NOT_FOUND, "已发布虚拟关系不存在: " + relationCode);
            }
            if (candidates.size() > 1) {
                throw error(DataPreviewErrorCode.RELATION_AMBIGUOUS, "虚拟关系编码对应多个目标模型: " + relationCode);
            }
            VirtualCatalogDescriptor.Relation relation = candidates.get(0);
            Set<String> enabledRemoteFields = remoteFields.computeIfAbsent(relationCode, ignored -> enabledFields(
                    catalogGateway.describePublished(relation.targetEntityCode(), null)
            ));
            if (!enabledRemoteFields.contains(remoteField)) {
                if (optional) {
                    unknownOutputFields.add(field);
                    continue;
                }
                throw error(DataPreviewErrorCode.FIELD_NOT_FOUND, "关系虚拟字段不存在或未启用: " + field);
            }
            knownFields.add(field);
            relationCodes.add(relationCode);
        }
        return new PublishedFieldResolution(knownFields, unknownOutputFields, List.copyOf(relationCodes));
    }

    private void enforceFieldDecision(
            Set<String> requestedFields,
            DataPreviewAccessPolicy.AccessDecision decision
    ) {
        if (decision == null) {
            throw error(DataPreviewErrorCode.FIELD_FORBIDDEN, "数据预览访问策略未返回授权结论");
        }
        LinkedHashSet<String> denied = new LinkedHashSet<>(requestedFields);
        denied.removeAll(decision.allowedFields());
        if (!denied.isEmpty()) {
            throw error(DataPreviewErrorCode.FIELD_FORBIDDEN, "无权预览虚拟字段: " + denied);
        }
    }

    /** 强制行过滤首期只允许当前实体字段，避免权限策略隐式引入未声明关系查询。 */
    private void validateEnforcedRowFilter(VirtualCatalogDescriptor catalog, FilterNode node) {
        validateEnforcedRowFilter(catalog, node, 0,
                Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private void validateEnforcedRowFilter(
            VirtualCatalogDescriptor catalog,
            FilterNode node,
            int depth,
            Set<FilterNode> seen
    ) {
        if (node == null) {
            return;
        }
        if (depth > MAX_POLICY_FILTER_DEPTH || !seen.add(node)) {
            throw error(DataPreviewErrorCode.FILTER_INVALID, "行级策略过滤树深度非法");
        }
        FilterType type = node.getType();
        if (type == FilterType.PREDICATE) {
            String field = requireField(node.getField());
            if (field.contains(".") || !enabledFields(catalog).contains(field)) {
                throw error(DataPreviewErrorCode.FIELD_NOT_FOUND, "行级策略引用了非法虚拟字段: " + field);
            }
            if (node.getOperator() == null) {
                throw error(DataPreviewErrorCode.FILTER_INVALID, "行级策略谓词缺少 operator");
            }
            validateFilterNodeValues(node);
            return;
        }
        if (type != FilterType.AND && type != FilterType.OR && type != FilterType.NOT) {
            throw error(DataPreviewErrorCode.FILTER_INVALID, "行级策略包含未知过滤节点");
        }
        List<FilterNode> children = node.getChildren();
        if (children == null || children.isEmpty()) {
            throw error(DataPreviewErrorCode.FILTER_INVALID, "行级策略组合节点必须包含 children");
        }
        if (type == FilterType.NOT && children.size() != 1) {
            throw error(DataPreviewErrorCode.FILTER_INVALID, "行级策略 NOT 必须恰好包含一个 child");
        }
        children.forEach(child -> {
            if (child == null) {
                throw error(DataPreviewErrorCode.FILTER_INVALID, "行级策略 child 不能为空");
            }
            validateEnforcedRowFilter(catalog, child, depth + 1, seen);
        });
    }

    private List<VirtualSort> buildSorts(
            List<DataPreviewQueryRequest.Sort> source,
            Map<String, String> allowedSortFields,
            Set<String> ignoredOutputFields
    ) {
        List<DataPreviewQueryRequest.Sort> sorts = safe(source);
        requireMaxSize(sorts, MAX_SORTS, "sorts");
        List<VirtualSort> result = new ArrayList<>();
        for (DataPreviewQueryRequest.Sort sort : sorts) {
            if (sort == null) {
                throw error(DataPreviewErrorCode.SORT_INVALID, "sort 不能为空");
            }
            String requested = requireField(sort.getField());
            if (ignoredOutputFields.contains(requested)) {
                continue;
            }
            String field = allowedSortFields.get(requested);
            if (field == null) {
                throw error(DataPreviewErrorCode.SORT_INVALID, "排序字段必须是预览输出字段: " + requested);
            }
            SortDirection direction;
            try {
                direction = StringUtils.hasText(sort.getDirection())
                        ? SortDirection.valueOf(sort.getDirection().trim().toUpperCase(Locale.ROOT))
                        : SortDirection.ASC;
            } catch (IllegalArgumentException exception) {
                throw error(DataPreviewErrorCode.SORT_INVALID, "不支持的排序方向: " + sort.getDirection());
            }
            VirtualSort target = new VirtualSort();
            target.setField(field);
            target.setDirection(direction);
            result.add(target);
        }
        return result;
    }

    private Set<String> optionalOutputFields(QueryShape shape, Set<String> filterFields) {
        LinkedHashSet<String> result = new LinkedHashSet<>(shape.outputFields());
        result.removeAll(filterFields);
        return result;
    }

    private List<String> executableListFields(
            QueryShape shape,
            Set<String> unknownOutputFields,
            VirtualCatalogDescriptor catalog
    ) {
        List<String> fields = shape.fields().stream()
                .filter(field -> !unknownOutputFields.contains(field))
                .toList();
        if (!fields.isEmpty()) {
            return fields;
        }
        // 下游将空 fields 解释为“查询全部已启用字段”，这里用一个受控字段避免该退化。
        String fallback = fallbackExecutionField(catalog);
        return fallback == null ? List.of() : List.of(fallback);
    }

    private List<VirtualGroupBy> executableGroupings(
            List<VirtualGroupBy> groupings,
            Set<String> unknownOutputFields
    ) {
        return groupings.stream()
                .filter(grouping -> !unknownOutputFields.contains(grouping.getField()))
                .toList();
    }

    private List<VirtualAggregate> executableAggregates(
            List<VirtualAggregate> aggregates,
            Set<String> unknownOutputFields
    ) {
        return aggregates.stream()
                .filter(aggregate -> !unknownOutputFields.contains(aggregate.getField()))
                .toList();
    }

    private Set<String> unknownOutputSortReferences(
            QueryShape shape,
            Set<String> unknownOutputFields
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (DataPreviewQueryResponse.Column column : shape.columns()) {
            if (unknownOutputFields.contains(column.getField())) {
                result.add(column.getField());
                result.add(column.getKey());
            }
        }
        return result;
    }

    private String fallbackExecutionField(VirtualCatalogDescriptor catalog) {
        if (catalog == null) {
            return null;
        }
        String primaryKey = catalog.fields().stream()
                .filter(VirtualCatalogDescriptor.Field::enabled)
                .filter(VirtualCatalogDescriptor.Field::primaryKey)
                .map(VirtualCatalogDescriptor.Field::code)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        if (primaryKey != null) {
            return primaryKey;
        }
        return catalog.fields().stream()
                .filter(VirtualCatalogDescriptor.Field::enabled)
                .map(VirtualCatalogDescriptor.Field::code)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private VirtualAggregate fallbackCount(String field) {
        VirtualAggregate aggregate = new VirtualAggregate();
        aggregate.setField(field);
        aggregate.setFunction(AggregateFunction.COUNT);
        aggregate.setAlias("__preview_fallback_count");
        return aggregate;
    }

    private DataPreviewQueryResponse response(
            VirtualCatalogDescriptor catalog,
            QueryShape shape,
            VirtualQueryResponse result
    ) {
        VirtualQueryResponse safeResult = result == null ? new VirtualQueryResponse() : result;
        int rawRecordCount = safeResult.getRecords() == null ? 0 : safeResult.getRecords().size();
        List<Map<String, Object>> records = shape.queryType() == QueryType.LIST
                ? shapeListRecords(shape.columns(), safeResult.getRecords())
                : shapeAggregateRecords(shape.columns(), safeResult.getRecords());
        boolean sourceExceededLimit = rawRecordCount > MAX_PREVIEW_ROWS;
        if (sourceExceededLimit) {
            records = new ArrayList<>(records.subList(0, Math.min(MAX_PREVIEW_ROWS, records.size())));
        }
        long total = safeResult.getTotal() == null ? records.size() : Math.max(0L, safeResult.getTotal());
        if (total < records.size()) {
            total = records.size();
        }
        DataPreviewQueryResponse response = new DataPreviewQueryResponse();
        response.setModel(catalog.entityCode());
        response.setCatalogVersion(catalog.catalogVersion());
        response.setSourceRevision(SOURCE_REVISION_PREFIX + catalog.catalogVersion());
        response.setQueryType(shape.queryType().name());
        response.setColumns(shape.columns().stream()
                .map(column -> enrichColumn(catalog, column))
                .toList());
        response.setRecords(records);
        response.setTotal(total);
        response.setTruncated(sourceExceededLimit || total > records.size());
        response.setRequestId(safeResult.getRequestId());
        response.setExecutionMs(safeResult.getExecutionMs());
        response.setSummary(safeResult.getSummary() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(safeResult.getSummary()));
        return response;
    }

    private List<Map<String, Object>> shapeListRecords(
            List<DataPreviewQueryResponse.Column> columns,
            List<Map<String, Object>> source
    ) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>(Math.min(source.size(), MAX_PREVIEW_ROWS));
        for (Map<String, Object> row : source) {
            Map<String, Object> shaped = new LinkedHashMap<>();
            for (DataPreviewQueryResponse.Column column : columns) {
                shaped.put(column.getKey(), projectedValue(row, column.getField()));
            }
            result.add(shaped);
            if (result.size() == MAX_PREVIEW_ROWS) {
                break;
            }
        }
        return result;
    }

    private List<Map<String, Object>> shapeAggregateRecords(
            List<DataPreviewQueryResponse.Column> columns,
            List<Map<String, Object>> source
    ) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>(Math.min(source.size(), MAX_PREVIEW_ROWS));
        for (Map<String, Object> row : source) {
            Map<String, Object> shaped = new LinkedHashMap<>();
            for (DataPreviewQueryResponse.Column column : columns) {
                shaped.put(column.getKey(), aggregateValue(row, column));
            }
            result.add(shaped);
            if (result.size() == MAX_PREVIEW_ROWS) {
                break;
            }
        }
        return result;
    }

    private Object aggregateValue(
            Map<String, Object> row,
            DataPreviewQueryResponse.Column column
    ) {
        if (row == null) {
            return null;
        }
        if (row.containsKey(column.getKey())) {
            return row.get(column.getKey());
        }
        return row.get(column.getField());
    }

    private DataPreviewQueryResponse.Column enrichColumn(
            VirtualCatalogDescriptor catalog,
            DataPreviewQueryResponse.Column source
    ) {
        DataPreviewQueryResponse.Column column = new DataPreviewQueryResponse.Column();
        column.setKey(source.getKey());
        column.setField(source.getField());
        column.setAggregation(source.getAggregation());

        VirtualCatalogDescriptor.Field field = findField(catalog, source.getField());
        String catalogName = field == null ? null : field.name();
        String sourceLabel = source.getLabel();
        column.setLabel(StringUtils.hasText(sourceLabel)
                && !sameText(sourceLabel, source.getField())
                && !sameText(sourceLabel, source.getKey())
                ? sourceLabel
                : firstText(catalogName, sourceLabel, source.getField(), source.getKey()));
        column.setDataType(resolveDataType(source, field));
        return column;
    }

    private VirtualCatalogDescriptor.Field findField(
            VirtualCatalogDescriptor catalog,
            String fieldCode
    ) {
        if (catalog == null || !StringUtils.hasText(fieldCode)) {
            return null;
        }
        return catalog.fields().stream()
                .filter(field -> fieldCode.equals(field.code()))
                .findFirst()
                .orElse(null);
    }

    private String resolveDataType(
            DataPreviewQueryResponse.Column column,
            VirtualCatalogDescriptor.Field field
    ) {
        if (StringUtils.hasText(column.getAggregation())) {
            return "number";
        }
        if (field == null || field.logicalType() == null) {
            return null;
        }
        return switch (field.logicalType()) {
            case BOOLEAN -> "boolean";
            case INTEGER, LONG, DECIMAL -> "number";
            case DATE -> "date";
            case TIMESTAMP -> "datetime";
            case STRING -> "string";
            default -> "unknown";
        };
    }

    private boolean sameText(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right)
                && left.trim().equalsIgnoreCase(right.trim());
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private Object projectedValue(Map<String, Object> row, String field) {
        if (row == null || !field.contains(".")) {
            return row == null ? null : row.get(field);
        }
        if (row.containsKey(field)) {
            return row.get(field);
        }
        int separator = field.indexOf('.');
        Object relation = row.get(field.substring(0, separator));
        String remoteField = field.substring(separator + 1);
        if (relation instanceof Map<?, ?> map) {
            return map.get(remoteField);
        }
        if (relation instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(item -> item.get(remoteField))
                    .toList();
        }
        return null;
    }

    private Long requestedCatalogVersion(String sourceRevision, Long catalogVersion) {
        if (catalogVersion != null && catalogVersion < 1) {
            throw error(DataPreviewErrorCode.SOURCE_REVISION_INVALID, "catalogVersion 必须为正整数");
        }
        if (!StringUtils.hasText(sourceRevision)) {
            throw error(DataPreviewErrorCode.SOURCE_REVISION_INVALID, "sourceRevision 不能为空");
        }
        Matcher matcher = SOURCE_REVISION_PATTERN.matcher(sourceRevision.trim());
        if (!matcher.matches()) {
            throw error(
                    DataPreviewErrorCode.SOURCE_REVISION_INVALID,
                    "sourceRevision 格式必须为 virtual-model/v{version} 或 v{version}"
            );
        }
        long revisionVersion;
        try {
            revisionVersion = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw error(DataPreviewErrorCode.SOURCE_REVISION_INVALID, "sourceRevision 版本超出范围");
        }
        if (catalogVersion != null && catalogVersion.longValue() != revisionVersion) {
            throw error(
                    DataPreviewErrorCode.SOURCE_REVISION_CONFLICT,
                    "sourceRevision 与 catalogVersion 不一致"
            );
        }
        return revisionVersion;
    }

    private FilterNode predicate(
            String field,
            FilterOperator operator,
            Object value,
            List<Object> values
    ) {
        FilterNode node = new FilterNode();
        node.setType(FilterType.PREDICATE);
        node.setField(field);
        node.setOperator(operator);
        node.setValue(value);
        node.setValues(values == null ? new ArrayList<>() : new ArrayList<>(values));
        return node;
    }

    private void validateFilterValues(
            DataPreviewQueryRequest.Filter filter,
            FilterOperator operator
    ) {
        List<Object> values = filter.getValues();
        if (operator == FilterOperator.IN || operator == FilterOperator.NOT_IN) {
            if (values == null || values.isEmpty() || values.size() > MAX_FILTER_VALUES) {
                throw error(DataPreviewErrorCode.FILTER_INVALID, operator + " 需要 1-" + MAX_FILTER_VALUES + " 个 values");
            }
            values.forEach(this::validateScalarValue);
            return;
        }
        if (operator == FilterOperator.IS_NULL || operator == FilterOperator.IS_NOT_NULL) {
            if (filter.getValue() != null || (values != null && !values.isEmpty())) {
                throw error(DataPreviewErrorCode.FILTER_INVALID, operator + " 不接受 value/values");
            }
            return;
        }
        validateScalarValue(filter.getValue());
        if (values != null && !values.isEmpty()) {
            throw error(DataPreviewErrorCode.FILTER_INVALID, operator + " 不接受 values");
        }
    }

    private void validateFilterNodeValues(FilterNode node) {
        FilterOperator operator = node.getOperator();
        List<Object> values = node.getValues();
        if (operator == FilterOperator.IN || operator == FilterOperator.NOT_IN) {
            if (values == null || values.isEmpty() || values.size() > MAX_FILTER_VALUES) {
                throw error(DataPreviewErrorCode.FILTER_INVALID, operator + " 需要 1-" + MAX_FILTER_VALUES + " 个 values");
            }
            values.forEach(this::validateScalarValue);
            return;
        }
        if (operator == FilterOperator.IS_NULL || operator == FilterOperator.IS_NOT_NULL) {
            if (node.getValue() != null || (values != null && !values.isEmpty())) {
                throw error(DataPreviewErrorCode.FILTER_INVALID, operator + " 不接受 value/values");
            }
            return;
        }
        validateScalarValue(node.getValue());
        if (values != null && !values.isEmpty()) {
            throw error(DataPreviewErrorCode.FILTER_INVALID, operator + " 不接受 values");
        }
    }

    private void validateScalarValue(Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Boolean) {
            return;
        }
        if (value instanceof Number number) {
            double numeric = number.doubleValue();
            if (Double.isNaN(numeric) || Double.isInfinite(numeric)) {
                throw error(DataPreviewErrorCode.FILTER_INVALID, "过滤值必须是有限数字");
            }
            return;
        }
        if (value instanceof CharSequence sequence) {
            if (sequence.length() > 4_096) {
                throw error(DataPreviewErrorCode.FILTER_INVALID, "过滤值文本过长");
            }
            return;
        }
        throw error(DataPreviewErrorCode.FILTER_INVALID, "过滤值必须是文本、数字或 null");
    }

    private void validateTimeBoundary(Object value, String label) {
        if (value == null) {
            return;
        }
        if (value instanceof Number number) {
            double numeric = number.doubleValue();
            if (Double.isNaN(numeric) || Double.isInfinite(numeric)) {
                throw error(DataPreviewErrorCode.TIME_RANGE_INVALID, label + " 必须是有限数字");
            }
            return;
        }
        if (value instanceof CharSequence sequence) {
            String text = sequence.toString().trim();
            if (text.isEmpty() || text.length() > 128
                    || !text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}(?:[T ][0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?(?:Z|[+-][0-9]{2}:[0-9]{2})?)?")) {
                throw error(DataPreviewErrorCode.TIME_RANGE_INVALID, label + " 必须是 ISO-8601 时间文本");
            }
            return;
        }
        if (value instanceof java.time.temporal.TemporalAccessor) {
            return;
        }
        throw error(DataPreviewErrorCode.TIME_RANGE_INVALID, label + " 类型不受支持");
    }

    private void validateRangeOrder(Object start, Object end) {
        if (start == null || end == null) {
            return;
        }
        if (start instanceof Number left && end instanceof Number right) {
            if (Double.compare(left.doubleValue(), right.doubleValue()) >= 0) {
                throw error(DataPreviewErrorCode.TIME_RANGE_INVALID, "timeRange start 必须早于 end");
            }
            return;
        }
        if (start instanceof Instant left && end instanceof Instant right) {
            if (!left.isBefore(right)) {
                throw error(DataPreviewErrorCode.TIME_RANGE_INVALID, "timeRange start 必须早于 end");
            }
            return;
        }
        if (start instanceof CharSequence left && end instanceof CharSequence right) {
            try {
                Instant leftInstant = Instant.parse(left.toString().replace(" ", "T"));
                Instant rightInstant = Instant.parse(right.toString().replace(" ", "T"));
                if (!leftInstant.isBefore(rightInstant)) {
                    throw error(DataPreviewErrorCode.TIME_RANGE_INVALID, "timeRange start 必须早于 end");
                }
            } catch (java.time.format.DateTimeParseException ignored) {
                // The physical engine may support non-ISO scalar date values; type/size
                // validation above still protects the boundary in that case.
            }
        }
    }

    private FilterNode and(List<FilterNode> nodes) {
        List<FilterNode> nonNull = nodes.stream().filter(Objects::nonNull).toList();
        if (nonNull.isEmpty()) {
            return null;
        }
        if (nonNull.size() == 1) {
            return nonNull.get(0);
        }
        FilterNode root = new FilterNode();
        root.setType(FilterType.AND);
        root.setChildren(new ArrayList<>(nonNull));
        return root;
    }

    private FilterNode and(FilterNode left, FilterNode right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return and(List.of(left, right));
    }

    private FilterOperator filterOperator(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "EQ";
        try {
            return FilterOperator.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw error(DataPreviewErrorCode.FILTER_INVALID, "不支持的过滤操作符: " + value);
        }
    }

    private AggregateFunction aggregateFunction(String value) {
        if (!StringUtils.hasText(value)) {
            throw error(DataPreviewErrorCode.AGGREGATE_INVALID, "measure.aggregation 不能为空");
        }
        try {
            return AggregateFunction.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw error(DataPreviewErrorCode.AGGREGATE_INVALID, "不支持的聚合函数: " + value);
        }
    }

    private Set<String> enabledFields(VirtualCatalogDescriptor catalog) {
        if (catalog == null) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        catalog.fields().stream()
                .filter(VirtualCatalogDescriptor.Field::enabled)
                .map(VirtualCatalogDescriptor.Field::code)
                .forEach(result::add);
        return Set.copyOf(result);
    }

    private VirtualPage previewPage(int limit) {
        VirtualPage page = new VirtualPage();
        page.setNumber(1);
        page.setSize(limit);
        return page;
    }

    private QueryHints previewHints() {
        QueryHints hints = new QueryHints();
        hints.setMaxPhysicalTasks(MAX_PHYSICAL_TASKS);
        hints.setMaxScanRows(MAX_SCAN_ROWS);
        hints.setTimeoutMs(TIMEOUT_MS);
        hints.setAllowLocalTransform(true);
        return hints;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_PREVIEW_ROWS;
        }
        return Math.max(1, Math.min(MAX_PREVIEW_ROWS, limit));
    }

    private void requireRequest(DataPreviewQueryRequest request) {
        if (request == null) {
            throw error(DataPreviewErrorCode.REQUEST_INVALID, "数据预览请求不能为空");
        }
    }

    /**
     * Reject unauthenticated calls before resolving a model/version. Besides being the
     * baseline security boundary, this prevents callers from using catalog errors as a
     * model-existence oracle.
     */
    private UserContext requireAuthenticatedUser() {
        Object current = SystemContext.getUserContext();
        if (!(current instanceof UserContext userContext)
                || userContext.subject() == null
                || userContext.subject().userId() == null) {
            throw error(DataPreviewErrorCode.AUTH_REQUIRED, "数据预览需要有效的用户主体");
        }
        return userContext;
    }

    private String requireModel(String value) {
        if (!StringUtils.hasText(value) || !MODEL_PATTERN.matcher(value.trim()).matches()) {
            throw error(DataPreviewErrorCode.REQUEST_INVALID, "model 必须是虚拟模型编码");
        }
        return value.trim();
    }

    private String requireField(String value) {
        if (!StringUtils.hasText(value) || !FIELD_PATTERN.matcher(value.trim()).matches()) {
            throw error(DataPreviewErrorCode.FIELD_NOT_FOUND, "非法虚拟字段编码: " + value);
        }
        return value.trim();
    }

    private String outputKey(String requested, String fallback) {
        String value = StringUtils.hasText(requested) ? requested.trim() : fallback;
        if (!ALIAS_PATTERN.matcher(value).matches()) {
            throw error(DataPreviewErrorCode.REQUEST_INVALID, "alias 必须是简单标识符: " + value);
        }
        return value;
    }

    private void requireUniqueOutputKey(Set<String> outputKeys, String key) {
        if (!outputKeys.add(key)) {
            throw error(DataPreviewErrorCode.REQUEST_INVALID, "输出 alias 重复: " + key);
        }
    }

    private void requireMaxSize(List<?> values, int max, String label) {
        if (values.size() > max) {
            throw error(DataPreviewErrorCode.REQUEST_INVALID, label + " 数量不能超过 " + max);
        }
    }

    private String label(String requested, String fallback) {
        return StringUtils.hasText(requested) ? requested.trim() : fallback;
    }

    private DataPreviewQueryResponse.Column column(
            String key,
            String field,
            String label,
            String aggregation
    ) {
        DataPreviewQueryResponse.Column column = new DataPreviewQueryResponse.Column();
        column.setKey(key);
        column.setField(field);
        column.setLabel(label);
        column.setAggregation(aggregation);
        return column;
    }

    private VirtualDataRuntimeException error(String code, String message) {
        return new VirtualDataRuntimeException(code, message);
    }

    private <T> List<T> safe(List<T> source) {
        return source == null ? List.of() : source;
    }

    private record Range(Object start, Object end) {
    }

    private record PublishedFieldResolution(
            Set<String> knownFields,
            Set<String> unknownOutputFields,
            List<String> relationCodes
    ) {
        private PublishedFieldResolution {
            knownFields = Set.copyOf(knownFields);
            unknownOutputFields = Set.copyOf(unknownOutputFields);
            relationCodes = List.copyOf(relationCodes);
        }
    }

    private record QueryShape(
            QueryType queryType,
            List<String> fields,
            List<VirtualGroupBy> groupings,
            List<VirtualAggregate> aggregates,
            Set<String> referencedFields,
            Map<String, String> sortFields,
            List<DataPreviewQueryResponse.Column> columns
    ) {
        private QueryShape {
            fields = List.copyOf(fields);
            groupings = List.copyOf(groupings);
            aggregates = List.copyOf(aggregates);
            referencedFields = Set.copyOf(referencedFields);
            sortFields = Map.copyOf(sortFields);
            columns = List.copyOf(columns);
        }

        private Set<String> outputFields() {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            columns.forEach(column -> result.add(column.getField()));
            return result;
        }
    }
}
