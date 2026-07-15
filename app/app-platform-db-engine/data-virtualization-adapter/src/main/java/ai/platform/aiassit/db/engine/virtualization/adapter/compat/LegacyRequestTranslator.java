package ai.platform.aiassit.db.engine.virtualization.adapter.compat;

import ai.platform.aiassit.data.virtualization.api.VirtualCatalogGateway;
import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualAggregate;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualCatalogDescriptor;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualGroupBy;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualPage;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualRelationRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualSort;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.AggregateFunction;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.QueryType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.RelationResultMode;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.SortDirection;
import ai.platform.aiassit.db.engine.api.dto.DbQueryAggregateExt;
import ai.platform.aiassit.db.engine.api.dto.DbQueryAggregateRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountDimension;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountExt;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountMetric;
import ai.platform.aiassit.db.engine.api.dto.DbQueryCountRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryExt;
import ai.platform.aiassit.db.engine.api.dto.DbQueryFilterCondition;
import ai.platform.aiassit.db.engine.api.dto.DbQueryGetExt;
import ai.platform.aiassit.db.engine.api.dto.DbQueryGetRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryListRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryPivotExt;
import ai.platform.aiassit.db.engine.api.dto.DbQueryPivotRequest;
import ai.platform.aiassit.db.engine.api.dto.DbQueryRelation;
import ai.platform.aiassit.db.engine.api.dto.DbQuerySort;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeExt;
import ai.platform.aiassit.db.engine.api.dto.DbQueryTreeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 将已发布 DbQuery v1 请求翻译为不包含物理表信息的虚拟查询请求。 */
public class LegacyRequestTranslator {

    static final String INVALID_REQUEST = "LEGACY_QUERY_INVALID";
    static final String INVALID_FIELD = "LEGACY_QUERY_FIELD_INVALID";
    static final String INVALID_RELATION = "LEGACY_QUERY_RELATION_INVALID";
    static final String RELATION_METADATA_MISMATCH = "LEGACY_QUERY_RELATION_METADATA_MISMATCH";
    static final String UNSUPPORTED_OPTION = "LEGACY_QUERY_OPTION_UNSUPPORTED";
    static final String UNSUPPORTED_AGGREGATE = "LEGACY_QUERY_AGGREGATE_UNSUPPORTED";
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 1000;
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyRequestTranslator.class);

    private final VirtualCatalogGateway catalogGateway;
    private final LegacyFilterAstParser filterParser;

    public LegacyRequestTranslator(VirtualCatalogGateway catalogGateway) {
        this(catalogGateway, new LegacyFilterAstParser());
    }

    LegacyRequestTranslator(VirtualCatalogGateway catalogGateway, LegacyFilterAstParser filterParser) {
        this.catalogGateway = Objects.requireNonNull(catalogGateway, "catalogGateway");
        this.filterParser = Objects.requireNonNull(filterParser, "filterParser");
    }

    public Translation translateGet(DbQueryGetRequest source) {
        requireRequest(source, "query.get");
        VirtualCatalogDescriptor catalog = catalog(source.getModel());
        DbQueryGetExt ext = source.getExt() == null ? new DbQueryGetExt() : source.getExt();
        RelationTranslation relations = translateRelations(ext.getRelations(), catalog, true);
        List<String> fields = validateDetailFields(ext.getFields(), catalog, relations.targetCatalogs());
        FilterNode filter = translateFilter(
                source.getFilterDict(), source.getFilterExpr(), catalog, relations.targetCatalogs());
        if (source.getId() != null) {
            List<VirtualCatalogDescriptor.Field> primaryKeys = catalog.primaryKeys();
            if (primaryKeys.size() != 1) {
                throw error(INVALID_REQUEST, "query.get 的 id 简写只适用于单一虚拟主键实体");
            }
            Map<String, Object> idFilter = new LinkedHashMap<>();
            idFilter.put(primaryKeys.get(0).code(), source.getId());
            filter = and(filter, filterParser.parse(idFilter, null));
        }
        VirtualQueryRequest target = base(source.getTitle(), catalog, QueryType.GET, fields, filter, relations);
        target.setSorts(translateDetailSorts(ext.getSorts(), catalog, relations.targetCatalogs()));
        target.setPage(page(1, 1));
        return new Translation(target, fields, 1, 1, false);
    }

    public Translation translateList(DbQueryListRequest source) {
        requireRequest(source, "query.list");
        VirtualCatalogDescriptor catalog = catalog(source.getModel());
        DbQueryExt ext = source.getExt() == null ? new DbQueryExt() : source.getExt();
        RelationTranslation relations = translateRelations(ext.getRelations(), catalog, true);
        List<String> fields = validateDetailFields(ext.getFields(), catalog, relations.targetCatalogs());
        int pageNumber = normalizePage(source.getPage());
        int pageSize = normalizePageSize(source.getPageSize());
        VirtualQueryRequest target = base(
                source.getTitle(),
                catalog,
                QueryType.LIST,
                fields,
                translateFilter(
                        source.getFilterDict(), source.getFilterExpr(), catalog, relations.targetCatalogs()),
                relations
        );
        target.setSorts(stableListSorts(ext.getSorts(), catalog, relations.targetCatalogs()));
        target.setPage(page(pageNumber, pageSize));
        target.setExactTotal(true);
        return new Translation(target, fields, pageNumber, pageSize, false);
    }

    public Translation translateCount(DbQueryCountRequest source) {
        requireRequest(source, "query.count");
        DbQueryCountExt ext = source.getExt() == null ? new DbQueryCountExt() : source.getExt();
        boolean plainCount = empty(source.getDimensions()) && empty(source.getMetrics());
        return translateAggregate(
                source.getTitle(),
                source.getModel(),
                source.getFilterDict(),
                source.getFilterExpr(),
                source.getDimensions(),
                source.getMetrics(),
                source.getHaving(),
                source.getSorts(),
                source.getPage(),
                source.getPageSize(),
                ext.getRelations(),
                ext.getTimeGrain(),
                ext.getTopN(),
                plainCount
        );
    }

    public Translation translateAggregate(DbQueryAggregateRequest source) {
        requireRequest(source, "query.aggregate");
        DbQueryAggregateExt ext = source.getExt() == null ? new DbQueryAggregateExt() : source.getExt();
        return translateAggregate(
                source.getTitle(),
                source.getModel(),
                source.getFilterDict(),
                source.getFilterExpr(),
                source.getDimensions(),
                source.getMetrics(),
                source.getHaving(),
                source.getSorts(),
                source.getPage(),
                source.getPageSize(),
                ext.getRelations(),
                ext.getTimeGrain(),
                ext.getTopN(),
                false
        );
    }

    public Translation translateTree(DbQueryTreeRequest source) {
        requireRequest(source, "query.tree");
        VirtualCatalogDescriptor catalog = catalog(source.getModel());
        DbQueryTreeExt ext = source.getExt() == null ? new DbQueryTreeExt() : source.getExt();
        RelationTranslation relations = translateRelations(ext.getRelations(), catalog, true);
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        fields.add(valueOrDefault(ext.getIdField(), "id"));
        fields.add(valueOrDefault(ext.getParentField(), "parent_id"));
        fields.add(valueOrDefault(ext.getLabelField(), "name"));
        if (source.getFields() != null) {
            fields.addAll(source.getFields());
        }
        List<String> validatedFields = validateDetailFields(
                new ArrayList<>(fields), catalog, relations.targetCatalogs());
        List<String> outputFields = empty(source.getFields())
                ? defaultDetailFields(catalog, relations.targetCatalogs()) : validatedFields;
        if (!empty(source.getMetrics()) || (source.getHaving() != null && !source.getHaving().isEmpty())) {
            LOGGER.warn("query.tree 的 metrics/having 在 DbQuery v1 中不生效，已忽略, model={}", source.getModel());
        }
        VirtualQueryRequest target = base(
                source.getTitle(),
                catalog,
                QueryType.LIST,
                outputFields,
                translateFilter(
                        source.getFilterDict(), source.getFilterExpr(), catalog, relations.targetCatalogs()),
                relations
        );
        target.setSorts(translateDetailSorts(source.getSorts(), catalog, relations.targetCatalogs()));
        target.setPage(page(1, MAX_PAGE_SIZE));
        target.setExactTotal(true);
        return new Translation(target, outputFields, 1, MAX_PAGE_SIZE, false);
    }

    public Translation translatePivot(DbQueryPivotRequest source) {
        requireRequest(source, "query.pivot");
        DbQueryPivotExt ext = source.getExt() == null ? new DbQueryPivotExt() : source.getExt();
        if (hasText(ext.getTimeGrain())) {
            throw error(UNSUPPORTED_OPTION, "query.pivot 暂不支持 timeGrain");
        }
        if (ext.getTopN() != null) {
            throw error(UNSUPPORTED_OPTION, "query.pivot 的 topN 语义尚未版本化，暂不支持");
        }
        List<DbQueryCountDimension> dimensions = new ArrayList<>();
        if (source.getRows() != null) {
            dimensions.addAll(source.getRows());
        }
        if (source.getColumns() != null) {
            dimensions.addAll(source.getColumns());
        }
        if (empty(source.getRows()) || empty(source.getColumns()) || empty(source.getMetrics())) {
            throw error(INVALID_REQUEST, "query.pivot 必须提供 rows、columns 和 metrics");
        }
        return translateAggregate(
                source.getTitle(),
                source.getModel(),
                source.getFilterDict(),
                source.getFilterExpr(),
                dimensions,
                source.getMetrics(),
                source.getHaving(),
                List.of(),
                1,
                MAX_PAGE_SIZE,
                ext.getRelations(),
                null,
                null,
                false
        );
    }

    private Translation translateAggregate(
            String title,
            String model,
            Map<String, Object> filters,
            String filterExpr,
            List<DbQueryCountDimension> dimensions,
            List<DbQueryCountMetric> metrics,
            Map<String, DbQueryFilterCondition> having,
            List<DbQuerySort> sorts,
            Integer requestedPage,
            Integer requestedPageSize,
            List<DbQueryRelation> requestedRelations,
            String timeGrain,
            Integer topN,
            boolean plainCount
    ) {
        VirtualCatalogDescriptor catalog = catalog(model);
        RelationTranslation relations = translateRelations(requestedRelations, catalog, false);
        int pageNumber = normalizePage(requestedPage);
        int pageSize = normalizePageSize(requestedPageSize);
        if (hasText(timeGrain) || topN != null) {
            LOGGER.warn("DbQuery v1 的 timeGrain/topN 不参与 count/aggregate 执行, model={}", model);
        }
        VirtualQueryRequest target = base(
                title,
                catalog,
                plainCount ? QueryType.COUNT : QueryType.AGGREGATE,
                List.of(),
                translateFilter(filters, filterExpr, catalog, relations.targetCatalogs()),
                relations
        );
        target.setPage(page(pageNumber, pageSize));
        target.setExactTotal(true);
        if (!plainCount) {
            AggregateTranslation aggregate = translateAggregateShape(
                    dimensions, metrics, catalog, relations.targetCatalogs());
            target.setGroupings(aggregate.groupings());
            target.setGroupBy(aggregate.groupings().stream().map(VirtualGroupBy::getField).toList());
            target.setAggregates(aggregate.aggregates());
            target.setHaving(translateHaving(having, aggregate.aliases()));
            target.setSorts(translateAliasSorts(sorts, aggregate.aliases()));
        }
        return new Translation(target, List.of(), pageNumber, pageSize, plainCount);
    }

    private AggregateTranslation translateAggregateShape(
            List<DbQueryCountDimension> dimensions,
            List<DbQueryCountMetric> metrics,
            VirtualCatalogDescriptor catalog,
            Map<String, VirtualCatalogDescriptor> relationCatalogs
    ) {
        List<VirtualGroupBy> groupings = new ArrayList<>();
        List<VirtualAggregate> aggregates = new ArrayList<>();
        Set<String> aliases = new LinkedHashSet<>();
        if (dimensions != null) {
            for (DbQueryCountDimension dimension : dimensions) {
                if (dimension == null) {
                    throw error(INVALID_REQUEST, "dimension 不能为空");
                }
                String field = validateField(dimension.getField(), catalog, relationCatalogs);
                String alias = hasText(dimension.getAlias()) ? identifier(dimension.getAlias(), "dimension alias") : field;
                requireUniqueAlias(aliases, alias);
                VirtualGroupBy grouping = new VirtualGroupBy();
                grouping.setField(field);
                grouping.setAlias(alias);
                groupings.add(grouping);
            }
        }
        List<DbQueryCountMetric> effectiveMetrics = metrics == null ? List.of() : metrics;
        if (effectiveMetrics.isEmpty()) {
            VirtualAggregate aggregate = new VirtualAggregate();
            aggregate.setFunction(AggregateFunction.COUNT);
            aggregate.setAlias("count");
            aggregates.add(aggregate);
            requireUniqueAlias(aliases, "count");
        } else {
            for (DbQueryCountMetric metric : effectiveMetrics) {
                if (metric == null) {
                    throw error(INVALID_REQUEST, "metric 不能为空");
                }
                AggregateFunction function = aggregateFunction(metric.getFunc());
                String field = null;
                if (hasText(metric.getField())) {
                    field = validateField(metric.getField(), catalog, relationCatalogs);
                } else if (function != AggregateFunction.COUNT) {
                    throw error(INVALID_REQUEST, function + " 指标必须提供 field");
                }
                String alias = hasText(metric.getAlias())
                        ? identifier(metric.getAlias(), "metric alias")
                        : function.name().toLowerCase(Locale.ROOT) + "_" + (field == null ? "all" : field);
                requireUniqueAlias(aliases, alias);
                VirtualAggregate aggregate = new VirtualAggregate();
                aggregate.setFunction(function);
                aggregate.setField(field);
                aggregate.setAlias(alias);
                aggregates.add(aggregate);
            }
        }
        return new AggregateTranslation(groupings, aggregates, aliases);
    }

    private FilterNode translateHaving(Map<String, DbQueryFilterCondition> having, Set<String> aliases) {
        if (having == null || having.isEmpty()) {
            return null;
        }
        for (String field : having.keySet()) {
            String alias = identifier(field, "having alias");
            if (!aliases.contains(alias)) {
                throw error(INVALID_FIELD, "having 只能引用分组或聚合别名: " + alias);
            }
        }
        return filterParser.parse(having, null);
    }

    private List<VirtualSort> translateAliasSorts(List<DbQuerySort> sorts, Set<String> aliases) {
        if (sorts == null || sorts.isEmpty()) {
            return List.of();
        }
        List<VirtualSort> result = new ArrayList<>();
        for (DbQuerySort sort : sorts) {
            if (sort == null || !hasText(sort.getField())) {
                continue;
            }
            String field = identifier(sort.getField(), "sort alias");
            if (!aliases.contains(field)) {
                throw error(INVALID_FIELD, "聚合排序只能引用分组或聚合别名: " + field);
            }
            result.add(sort(field, sort.getOrder()));
        }
        return result;
    }

    private VirtualQueryRequest base(
            String title,
            VirtualCatalogDescriptor catalog,
            QueryType queryType,
            List<String> fields,
            FilterNode filter,
            RelationTranslation relations
    ) {
        VirtualQueryRequest target = new VirtualQueryRequest();
        target.setEntityCode(catalog.entityCode());
        target.setCatalogVersion(catalog.catalogVersion());
        target.setTraceLabel(title);
        target.setQueryType(queryType);
        target.setFields(new ArrayList<>(fields));
        target.setFilter(filter);
        target.setRelationCodes(new ArrayList<>(relations.aliases()));
        target.setRelations(new ArrayList<>(relations.requests()));
        return target;
    }

    private RelationTranslation translateRelations(
            List<DbQueryRelation> source,
            VirtualCatalogDescriptor catalog,
            boolean defaultForwardRelations
    ) {
        if (source == null || source.isEmpty()) {
            return defaultForwardRelations
                    ? translateDefaultForwardRelations(catalog)
                    : new RelationTranslation(List.of(), Map.of(), List.of());
        }
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        Map<String, VirtualCatalogDescriptor> targetCatalogs = new LinkedHashMap<>();
        Map<String, VirtualCatalogDescriptor> catalogsByModel = new LinkedHashMap<>();
        List<VirtualRelationRequest> requests = new ArrayList<>();
        for (DbQueryRelation relation : source) {
            String alias = relationAlias(relation == null ? null : relation.getKey());
            String model = relationModel(relation == null ? null : relation.getModel());
            if (!aliases.add(alias)) {
                throw error(INVALID_RELATION, "关系别名重复: " + alias);
            }
            if (catalog.fields().stream().anyMatch(field -> field.enabled() && alias.equals(field.code()))) {
                throw error(INVALID_RELATION, "关系别名不能与主虚拟表字段同名: " + alias);
            }
            validateRelationType(relation, alias);

            VirtualCatalogDescriptor targetCatalog = catalogsByModel.computeIfAbsent(model, this::catalog);
            Map<String, String> requestedOn = normalizeRelationFields(
                    relation.getOn(), catalog, targetCatalog, alias);
            List<PublishedRelation> available = catalog.relations().stream()
                    .filter(item -> model.equals(item.targetEntityCode()))
                    .map(item -> new PublishedRelation(
                            item,
                            normalizeRelationFields(item.localToRemoteFields(), catalog, targetCatalog, alias)
                    ))
                    .toList();
            List<PublishedRelation> candidates = available.stream()
                    .filter(item -> requestedOn.isEmpty() || requestedOn.equals(item.localToRemoteFields()))
                    .toList();

            if (!available.isEmpty() && !requestedOn.isEmpty() && candidates.isEmpty()) {
                throw error(RELATION_METADATA_MISMATCH,
                        "relation.on 与已发布虚拟关系不一致: " + model);
            }
            candidates = collapseEquivalentRelations(candidates);

            if (candidates.size() > 1) {
                String suffix = requestedOn.isEmpty() ? "，请提供 on 唯一匹配" : "，on 仍无法唯一匹配";
                throw error(INVALID_RELATION, "目标虚拟表存在多个已发布关系: " + model + suffix);
            }

            PublishedRelation published = candidates.isEmpty() ? null : candidates.get(0);
            if (published == null && requestedOn.isEmpty()) {
                throw error(INVALID_RELATION, "未找到目标虚拟表的已发布关系，必须提供 on: " + model);
            }

            Map<String, String> fields = published == null
                    ? requestedOn : published.localToRemoteFields();
            RelationResultMode resultMode = published == null
                    ? inferAdHocResultMode(fields, targetCatalog) : published.descriptor().resultMode();
            FilterNode relationFilter = filterParser.parse(relation.getFilter(), null);
            validateFilterFields(relationFilter, targetCatalog, Map.of());

            VirtualRelationRequest target = new VirtualRelationRequest();
            target.setKey(alias);
            target.setRelationCode(published == null ? null : published.descriptor().code());
            target.setTargetEntityCode(model);
            target.setLocalToRemoteFields(new LinkedHashMap<>(fields));
            target.setResultMode(resultMode);
            target.setFilter(relationFilter);
            requests.add(target);
            targetCatalogs.put(alias, targetCatalog);
        }
        return new RelationTranslation(new ArrayList<>(aliases), targetCatalogs, requests);
    }

    private RelationTranslation translateDefaultForwardRelations(VirtualCatalogDescriptor catalog) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        Map<String, VirtualCatalogDescriptor> targetCatalogs = new LinkedHashMap<>();
        Map<String, VirtualCatalogDescriptor> catalogsByModel = new LinkedHashMap<>();
        List<VirtualRelationRequest> requests = new ArrayList<>();
        for (VirtualCatalogDescriptor.Relation relation : catalog.relations()) {
            if (relation.reverse()) {
                continue;
            }
            String alias = relationAlias(relation.code());
            String model = relationModel(relation.targetEntityCode());
            if (!aliases.add(alias)) {
                throw error(INVALID_RELATION, "默认正向关系别名重复: " + alias);
            }
            if (catalog.fields().stream().anyMatch(field -> field.enabled() && alias.equals(field.code()))) {
                throw error(INVALID_RELATION, "默认正向关系别名不能与主虚拟表字段同名: " + alias);
            }
            VirtualCatalogDescriptor targetCatalog = catalogsByModel.computeIfAbsent(model, this::catalog);
            Map<String, String> fields = normalizeRelationFields(
                    relation.localToRemoteFields(), catalog, targetCatalog, alias);

            VirtualRelationRequest target = new VirtualRelationRequest();
            target.setKey(alias);
            target.setRelationCode(relation.code());
            target.setTargetEntityCode(model);
            target.setLocalToRemoteFields(new LinkedHashMap<>(fields));
            target.setResultMode(relation.resultMode());
            requests.add(target);
            targetCatalogs.put(alias, targetCatalog);
        }
        return new RelationTranslation(new ArrayList<>(aliases), targetCatalogs, requests);
    }

    private List<PublishedRelation> collapseEquivalentRelations(List<PublishedRelation> source) {
        Map<PublishedRelationSignature, PublishedRelation> unique = new LinkedHashMap<>();
        for (PublishedRelation relation : source) {
            unique.putIfAbsent(new PublishedRelationSignature(
                    relation.localToRemoteFields(), relation.descriptor().resultMode()), relation);
        }
        return new ArrayList<>(unique.values());
    }

    private void validateRelationType(DbQueryRelation source, String alias) {
        String type = valueOrDefault(source.getType(), "left").toLowerCase(Locale.ROOT);
        if (!"left".equals(type)) {
            throw error(UNSUPPORTED_OPTION, "DbQuery 虚拟关系首期只支持 LEFT JOIN: " + alias);
        }
    }

    private Map<String, String> normalizeRelationFields(
            Map<String, String> source,
            VirtualCatalogDescriptor localCatalog,
            VirtualCatalogDescriptor remoteCatalog,
            String alias
    ) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String local = simpleIdentifier(entry.getKey(), "relation on local field");
            String remote = simpleIdentifier(entry.getValue(), "relation on remote field");
            requireEnabledField(localCatalog, local, "关系本地字段不存在或未启用: " + alias + "." + local);
            requireEnabledField(remoteCatalog, remote, "关系目标字段不存在或未启用: " + alias + "." + remote);
            result.put(local, remote);
        }
        return result;
    }

    private RelationResultMode inferAdHocResultMode(
            Map<String, String> localToRemoteFields,
            VirtualCatalogDescriptor remoteCatalog
    ) {
        List<VirtualCatalogDescriptor.Field> primaryKeys = remoteCatalog.primaryKeys();
        if (primaryKeys.isEmpty()) {
            return RelationResultMode.COLLECTION;
        }
        Set<String> remoteFields = new LinkedHashSet<>(localToRemoteFields.values());
        boolean coversPrimaryKey = primaryKeys.stream().allMatch(field -> remoteFields.contains(field.code()));
        return coversPrimaryKey ? RelationResultMode.OBJECT : RelationResultMode.COLLECTION;
    }

    private String relationAlias(String source) {
        if (!hasText(source)) {
            throw error(INVALID_RELATION, "relation key 不能为空");
        }
        String value = source.trim();
        if (!value.matches("[A-Za-z0-9_]+")) {
            throw error(INVALID_RELATION, "relation key 格式非法: " + source);
        }
        return value;
    }

    private String relationModel(String source) {
        if (!hasText(source)) {
            throw error(INVALID_RELATION, "relation model 不能为空");
        }
        String value = source.trim();
        if (!value.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")) {
            throw error(INVALID_RELATION, "relation model 格式非法: " + source);
        }
        return value;
    }

    private FilterNode translateFilter(
            Map<String, ?> filters,
            String expression,
            VirtualCatalogDescriptor catalog,
            Map<String, VirtualCatalogDescriptor> relationCatalogs
    ) {
        FilterNode result = filterParser.parse(filters, expression);
        validateFilterFields(result, catalog, relationCatalogs);
        return result;
    }

    private void validateFilterFields(
            FilterNode node,
            VirtualCatalogDescriptor catalog,
            Map<String, VirtualCatalogDescriptor> relationCatalogs
    ) {
        if (node == null) {
            return;
        }
        if (node.getType() == FilterType.PREDICATE) {
            validateField(node.getField(), catalog, relationCatalogs);
        }
        if (node.getChildren() != null) {
            node.getChildren().forEach(child -> validateFilterFields(child, catalog, relationCatalogs));
        }
    }

    private List<String> validateDetailFields(
            List<String> fields,
            VirtualCatalogDescriptor catalog,
            Map<String, VirtualCatalogDescriptor> relationCatalogs
    ) {
        if (fields == null || fields.isEmpty()) {
            return defaultDetailFields(catalog, relationCatalogs);
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String field : fields) {
            result.add(validateField(field, catalog, relationCatalogs));
        }
        return new ArrayList<>(result);
    }

    private List<String> defaultDetailFields(
            VirtualCatalogDescriptor catalog,
            Map<String, VirtualCatalogDescriptor> relationCatalogs
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        catalog.fields().stream()
                .filter(VirtualCatalogDescriptor.Field::enabled)
                .map(VirtualCatalogDescriptor.Field::code)
                .forEach(result::add);
        relationCatalogs.forEach((alias, targetCatalog) -> targetCatalog.fields().stream()
                .filter(VirtualCatalogDescriptor.Field::enabled)
                .map(field -> alias + "." + field.code())
                .forEach(result::add));
        return new ArrayList<>(result);
    }

    private String validateField(
            String source,
            VirtualCatalogDescriptor catalog,
            Map<String, VirtualCatalogDescriptor> relationCatalogs
    ) {
        String field = identifier(source, "virtual field");
        int separator = field.indexOf('.');
        if (separator < 0) {
            requireEnabledField(catalog, field, "虚拟字段不存在或未启用: " + field);
            return field;
        }
        if (separator != field.lastIndexOf('.')) {
            throw error(INVALID_FIELD, "关系字段仅支持 key.field 格式: " + field);
        }
        String alias = field.substring(0, separator);
        String remoteField = field.substring(separator + 1);
        VirtualCatalogDescriptor targetCatalog = relationCatalogs.get(alias);
        if (targetCatalog == null) {
            throw error(INVALID_FIELD, "关系字段必须显式声明关系别名: " + field);
        }
        requireEnabledField(targetCatalog, remoteField, "关系目标字段不存在或未启用: " + field);
        return field;
    }

    private List<VirtualSort> translateDetailSorts(
            List<DbQuerySort> sorts,
            VirtualCatalogDescriptor catalog,
            Map<String, VirtualCatalogDescriptor> relationCatalogs
    ) {
        if (sorts == null || sorts.isEmpty()) {
            return List.of();
        }
        List<VirtualSort> result = new ArrayList<>();
        for (DbQuerySort source : sorts) {
            if (source == null || !hasText(source.getField())) {
                continue;
            }
            result.add(sort(validateField(source.getField(), catalog, relationCatalogs), source.getOrder()));
        }
        return result;
    }

    private List<VirtualSort> stableListSorts(
            List<DbQuerySort> sorts,
            VirtualCatalogDescriptor catalog,
            Map<String, VirtualCatalogDescriptor> relationCatalogs
    ) {
        List<VirtualSort> result = new ArrayList<>(translateDetailSorts(sorts, catalog, relationCatalogs));
        List<VirtualCatalogDescriptor.Field> primaryKeys = catalog.primaryKeys();
        if (primaryKeys.isEmpty()) {
            throw error("PLAN_EXACTNESS_UNPROVABLE", "query.list 需要虚拟主键以保证稳定分页: " + catalog.entityCode());
        }
        Set<String> existing = new LinkedHashSet<>();
        result.forEach(sort -> existing.add(sort.getField()));
        for (VirtualCatalogDescriptor.Field primaryKey : primaryKeys) {
            if (existing.add(primaryKey.code())) {
                result.add(sort(primaryKey.code(), "asc"));
            }
        }
        return result;
    }

    private VirtualSort sort(String field, String order) {
        VirtualSort target = new VirtualSort();
        target.setField(field);
        target.setDirection("desc".equalsIgnoreCase(order) ? SortDirection.DESC : SortDirection.ASC);
        return target;
    }

    private AggregateFunction aggregateFunction(String source) {
        String function = valueOrDefault(source, "count").toUpperCase(Locale.ROOT);
        try {
            return AggregateFunction.valueOf(function);
        } catch (IllegalArgumentException exception) {
            throw error(UNSUPPORTED_AGGREGATE, "不支持的聚合函数: " + source);
        }
    }

    private VirtualCatalogDescriptor catalog(String model) {
        String entityCode = identifier(model, "model/entityCode");
        return catalogGateway.describePublished(entityCode, null);
    }

    private FilterNode and(FilterNode left, FilterNode right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        FilterNode node = new FilterNode();
        node.setType(FilterType.AND);
        node.setChildren(new ArrayList<>(List.of(left, right)));
        return node;
    }

    private VirtualPage page(int number, int size) {
        VirtualPage page = new VirtualPage();
        page.setNumber(number);
        page.setSize(size);
        return page;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private void requireRequest(Object request, String operation) {
        if (request == null) {
            throw error(INVALID_REQUEST, operation + " 请求不能为空");
        }
    }

    private void requireUniqueAlias(Set<String> aliases, String alias) {
        if (!aliases.add(alias)) {
            throw error(INVALID_FIELD, "分组或聚合别名重复: " + alias);
        }
    }

    private String identifier(String source, String label) {
        if (!hasText(source)) {
            throw error(INVALID_FIELD, label + " 不能为空");
        }
        String value = source.trim();
        if (!value.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")) {
            throw error(INVALID_FIELD, label + " 格式非法: " + source);
        }
        return value;
    }

    private String simpleIdentifier(String source, String label) {
        if (!hasText(source)) {
            throw error(INVALID_FIELD, label + " 不能为空");
        }
        String value = source.trim();
        if (!value.matches("[A-Za-z0-9_]+")) {
            throw error(INVALID_FIELD, label + " 格式非法: " + source);
        }
        return value;
    }

    private void requireEnabledField(VirtualCatalogDescriptor catalog, String field, String message) {
        boolean exists = catalog.fields().stream().anyMatch(item -> item.enabled() && field.equals(item.code()));
        if (!exists) {
            throw error(INVALID_FIELD, message);
        }
    }

    private String valueOrDefault(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean empty(Collection<?> values) {
        return values == null || values.isEmpty();
    }

    private LegacyQueryCompatibilityException error(String code, String message) {
        return new LegacyQueryCompatibilityException(code, message);
    }

    public record Translation(
            VirtualQueryRequest request,
            List<String> outputFields,
            int page,
            int pageSize,
            boolean plainCount
    ) {
        public Translation {
            outputFields = outputFields == null ? List.of() : List.copyOf(outputFields);
        }
    }

    private record RelationTranslation(
            List<String> aliases,
            Map<String, VirtualCatalogDescriptor> targetCatalogs,
            List<VirtualRelationRequest> requests
    ) {
        private RelationTranslation {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            targetCatalogs = targetCatalogs == null
                    ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(targetCatalogs));
            requests = requests == null ? List.of() : List.copyOf(requests);
        }
    }

    private record PublishedRelation(
            VirtualCatalogDescriptor.Relation descriptor,
            Map<String, String> localToRemoteFields
    ) {
    }

    private record PublishedRelationSignature(
            Map<String, String> localToRemoteFields,
            RelationResultMode resultMode
    ) {
    }

    private record AggregateTranslation(
            List<VirtualGroupBy> groupings,
            List<VirtualAggregate> aggregates,
            Set<String> aliases
    ) {
    }
}
