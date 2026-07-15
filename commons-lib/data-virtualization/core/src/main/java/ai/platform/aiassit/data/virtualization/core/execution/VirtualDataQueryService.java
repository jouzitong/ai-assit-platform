package ai.platform.aiassit.data.virtualization.core.execution;

import ai.platform.aiassit.data.virtualization.api.VirtualQueryGateway;
import ai.platform.aiassit.data.virtualization.api.dto.QueryHints;
import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualExplainResponse;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualPage;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryResponse;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualRelationRequest;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.QueryType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.RelationResultMode;
import ai.platform.aiassit.data.virtualization.core.catalog.CatalogSnapshot;
import ai.platform.aiassit.data.virtualization.core.catalog.VirtualCatalogService;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.core.plan.PhysicalExecutionPlan;
import ai.platform.aiassit.data.virtualization.core.plan.PhysicalPlanGenerator;
import ai.platform.aiassit.data.virtualization.core.plan.VirtualLogicalPlan;
import ai.platform.aiassit.data.virtualization.core.plan.VirtualLogicalPlanCompiler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class VirtualDataQueryService implements VirtualQueryGateway {
    private final VirtualCatalogService catalogService;
    private final VirtualLogicalPlanCompiler logicalPlanCompiler;
    private final PhysicalPlanGenerator physicalPlanGenerator;
    private final PhysicalExecutionEngine executionEngine;
    private final VirtualResultFinalizer resultFinalizer;
    private final FilterEvaluator filterEvaluator;

    public VirtualDataQueryService(
            VirtualCatalogService catalogService,
            VirtualLogicalPlanCompiler logicalPlanCompiler,
            PhysicalPlanGenerator physicalPlanGenerator,
            PhysicalExecutionEngine executionEngine,
            VirtualResultFinalizer resultFinalizer,
            FilterEvaluator filterEvaluator
    ) {
        this.catalogService = catalogService;
        this.logicalPlanCompiler = logicalPlanCompiler;
        this.physicalPlanGenerator = physicalPlanGenerator;
        this.executionEngine = executionEngine;
        this.resultFinalizer = resultFinalizer;
        this.filterEvaluator = filterEvaluator;
    }

    @Override
    public VirtualQueryResponse query(VirtualQueryRequest request) {
        requireRequest(request);
        CatalogSnapshot snapshot = catalogService.requirePublished(request.getEntityCode(), request.getCatalogVersion());
        List<ResolvedRelation> relations = resolveRelations(snapshot, request);
        List<String> relationAliases = relations.stream().map(ResolvedRelation::alias).toList();
        request = normalizedRelations(request, relationAliases);
        validateRelationReferences(request, relationAliases);
        Map<String, RelationResultMode> relationModes = relationModes(relations);
        validateCollectionRelationUsage(request, relationModes);
        Set<String> joinFields = localJoinFields(relations);
        if (!relations.isEmpty()) {
            addStableIdentityFields(snapshot, joinFields);
        }
        boolean relationFilter = hasRelationFilter(request.getFilter());
        if (relationFilter && relations.isEmpty()) {
            throw new VirtualDataException("RELATION_NOT_FOUND", "关联字段过滤必须显式声明 relationCodes");
        }
        if (relationFilter) {
            collectLocalFilterFields(request.getFilter(), joinFields);
            logicalPlanCompiler.compile(snapshot, request, joinFields);
        }
        VirtualQueryRequest executionRequest = request;
        RawQueryResult exactCount = exactCount(snapshot, request, relationModes, relationFilter);
        if (relationFilter || request.getQueryType() == QueryType.COUNT && !relations.isEmpty()) {
            executionRequest = copy(request);
            executionRequest.setQueryType(QueryType.LIST);
            if (relationFilter) executionRequest.setFilter(null);
        }
        RawQueryResult raw = raw(snapshot, executionRequest, joinFields);
        RelationJoinResult joined = joinRelations(request, raw.executionRows().rows(), relations);
        List<Map<String, Object>> rows = joined.rows();
        FilterNode requestedFilter = request.getFilter();
        if (relationFilter) rows = rows.stream().filter(row -> filterEvaluator.test(requestedFilter, row)).toList();
        if (relationModes.containsValue(RelationResultMode.OBJECT)) {
            requireSingleValuedRelations(snapshot, rows);
        }
        VirtualQueryResponse response = resultFinalizer.finish(request, raw.plan(), raw.executionRows(), rows);
        response.setPhysicalTaskCount(response.getPhysicalTaskCount() + joined.physicalTaskCount());
        response.setExecutionMs(response.getExecutionMs() + joined.executionMs());
        if (exactCount != null) {
            response.setTotal(exactCount.executionRows().total());
            response.setPhysicalTaskCount(response.getPhysicalTaskCount() + exactCount.executionRows().physicalTaskCount());
            response.setExecutionMs(response.getExecutionMs() + exactCount.executionRows().executionMs());
        }
        log.info("virtual data query completed: planId={}, entityCode={}, catalogVersion={}, tasks={}, rows={}, executionMs={}",
                response.getPlanId(), request.getEntityCode(), response.getCatalogVersion(),
                response.getPhysicalTaskCount(), response.getRecords().size(), response.getExecutionMs());
        return response;
    }

    @Override
    public VirtualExplainResponse explain(VirtualQueryRequest request) {
        requireRequest(request);
        CatalogSnapshot snapshot = catalogService.requirePublished(request.getEntityCode(), request.getCatalogVersion());
        List<ResolvedRelation> relations = resolveRelations(snapshot, request);
        List<String> relationAliases = relations.stream().map(ResolvedRelation::alias).toList();
        request = normalizedRelations(request, relationAliases);
        validateRelationReferences(request, relationAliases);
        Map<String, RelationResultMode> relationModes = relationModes(relations);
        validateCollectionRelationUsage(request, relationModes);
        Set<String> extraFields = localJoinFields(relations);
        if (!relations.isEmpty()) addStableIdentityFields(snapshot, extraFields);
        boolean relationFilter = hasRelationFilter(request.getFilter());
        if (relationFilter) logicalPlanCompiler.compile(snapshot, request, extraFields);
        VirtualQueryRequest executionRequest = request;
        if (relationFilter || request.getQueryType() == QueryType.COUNT && !relations.isEmpty()) {
            executionRequest = copy(request);
            executionRequest.setQueryType(QueryType.LIST);
            if (relationFilter) {
                collectLocalFilterFields(request.getFilter(), extraFields);
                executionRequest.setFilter(null);
            }
        }
        VirtualLogicalPlan logicalPlan = logicalPlanCompiler.compile(snapshot, executionRequest, extraFields);
        PhysicalExecutionPlan plan = physicalPlanGenerator.generate(snapshot, logicalPlan);
        VirtualExplainResponse response = new VirtualExplainResponse();
        response.setPlanId(plan.planId());
        response.setEntityCode(snapshot.entityCode());
        response.setCatalogVersion(snapshot.catalogVersion());
        response.setWarnings(new ArrayList<>(plan.warnings()));
        if (!relations.isEmpty()) {
            response.getWarnings().add("关联关系将在应用层执行受预算保护的 Hash Join");
        }
        if (Boolean.TRUE.equals(request.getExactTotal()) && request.getQueryType() == QueryType.LIST && relations.isEmpty()) {
            response.getWarnings().add("LIST 将执行独立精确总数分支");
        }
        if (hasScopedRelationFilters(request)) {
            response.getWarnings().add("关系域过滤在远端关系分支执行，并保持 LEFT JOIN ON 语义");
        }
        if (relationFilter) response.getWarnings().add("包含关联字段的过滤条件将在 Join 完成后执行");
        for (PhysicalExecutionPlan.PhysicalTask task : plan.tasks()) {
            VirtualExplainResponse.Task item = new VirtualExplainResponse.Task();
            item.setTaskId(task.taskId());
            item.setBindingCode(task.binding().code());
            item.setSourceKey(task.binding().sourceKey());
            item.setPhysicalTable(task.binding().physicalTableName());
            item.setRouteReason(task.routeReason());
            item.setPhysicalColumns(task.transformRules().stream().flatMap(rule -> rule.physicalPorts().stream())
                    .map(CatalogSnapshot.Port::physicalColumnName).distinct().toList());
            item.setTransformRules(task.transformRules().stream().map(CatalogSnapshot.TransformRule::code).toList());
            response.getTasks().add(item);
        }
        return response;
    }

    private RawQueryResult raw(CatalogSnapshot snapshot, VirtualQueryRequest request, Set<String> extraFields) {
        VirtualLogicalPlan logicalPlan = logicalPlanCompiler.compile(snapshot, request, extraFields);
        PhysicalExecutionPlan plan = physicalPlanGenerator.generate(snapshot, logicalPlan);
        return new RawQueryResult(plan, executionEngine.execute(plan));
    }

    private RelationJoinResult joinRelations(
            VirtualQueryRequest request,
            List<Map<String, Object>> initialRows,
            List<ResolvedRelation> relations
    ) {
        if (relations == null || relations.isEmpty()) {
            return new RelationJoinResult(initialRows, 0, 0L);
        }
        List<Map<String, Object>> rows = initialRows;
        int physicalTaskCount = 0;
        long executionMs = 0L;
        for (ResolvedRelation relation : relations) {
            String alias = relation.alias();
            CatalogSnapshot remoteSnapshot = relation.remoteSnapshot();
            List<String> requestedRemote = requestedRelationFields(request, alias, remoteSnapshot);
            Set<String> remoteRequired = new LinkedHashSet<>(relation.remoteKeys());
            remoteRequired.addAll(requestedRemote);

            VirtualQueryRequest remoteRequest = new VirtualQueryRequest();
            remoteRequest.setEntityCode(remoteSnapshot.entityCode());
            remoteRequest.setCatalogVersion(remoteSnapshot.catalogVersion());
            remoteRequest.setQueryType(QueryType.LIST);
            remoteRequest.setFields(new ArrayList<>(remoteRequired));
            if (relation.filter() != null) {
                validateRelationFilterScope(relation.filter(), alias);
                remoteRequest.setFilter(relation.filter());
            }
            remoteRequest.setConsistency(request.getConsistency());
            remoteRequest.setHints(request.getHints() == null ? new QueryHints() : request.getHints());
            remoteRequest.setPage(new VirtualPage());
            RawQueryResult remoteRaw = raw(remoteSnapshot, remoteRequest, Set.of());
            physicalTaskCount += remoteRaw.executionRows().physicalTaskCount();
            executionMs += remoteRaw.executionRows().executionMs();
            rows = relation.effectiveMode() == RelationResultMode.COLLECTION
                    ? attachCollection(rows, remoteRaw.executionRows().rows(), relation.localKeys(), relation.remoteKeys(),
                    requestedRemote, alias, joinBudget(request))
                    : hashJoin(rows, remoteRaw.executionRows().rows(), relation.localKeys(), relation.remoteKeys(),
                    requestedRemote, alias, joinBudget(request));
        }
        return new RelationJoinResult(rows, physicalTaskCount, executionMs);
    }

    /**
     * 集合关系保持一条主实体记录，只把匹配的远端记录归组到 relationCode 对应的数组中。
     */
    private List<Map<String, Object>> attachCollection(
            List<Map<String, Object>> localRows,
            List<Map<String, Object>> remoteRows,
            List<String> localKeys,
            List<String> remoteKeys,
            List<String> remoteFields,
            String relationCode,
            int maxRows
    ) {
        if (remoteRows.size() > maxRows) {
            throw new VirtualDataException("PLAN_BUDGET_EXCEEDED", "关联集合结果超过 maxScanRows 预算: " + relationCode);
        }
        Map<List<Object>, List<Map<String, Object>>> index = relationIndex(remoteRows, remoteKeys);
        List<Map<String, Object>> result = new ArrayList<>(localRows.size());
        int relatedRecordCount = 0;
        for (Map<String, Object> local : localRows) {
            List<Object> localKey = localKeys.stream().map(local::get).toList();
            List<Map<String, Object>> matches = localKey.stream().anyMatch(java.util.Objects::isNull)
                    ? List.of() : index.getOrDefault(localKey, List.of());
            relatedRecordCount += matches.size();
            if (relatedRecordCount > maxRows) {
                throw new VirtualDataException("PLAN_BUDGET_EXCEEDED", "关联集合归组超过 maxScanRows 预算: " + relationCode);
            }
            List<Map<String, Object>> collection = new ArrayList<>(matches.size());
            for (Map<String, Object> remote : matches) {
                Map<String, Object> item = new LinkedHashMap<>();
                remoteFields.forEach(field -> item.put(field, remote.get(field)));
                collection.add(item);
            }
            Map<String, Object> joined = new LinkedHashMap<>(local);
            joined.put(relationCode, collection);
            result.add(joined);
        }
        return result;
    }

    private List<Map<String, Object>> hashJoin(
            List<Map<String, Object>> localRows,
            List<Map<String, Object>> remoteRows,
            List<String> localKeys,
            List<String> remoteKeys,
            List<String> remoteFields,
            String relationCode,
            int maxRows
    ) {
        if (remoteRows.size() > maxRows) {
            throw new VirtualDataException("PLAN_BUDGET_EXCEEDED", "关联表结果超过 maxScanRows 预算: " + relationCode);
        }
        Map<List<Object>, List<Map<String, Object>>> index = relationIndex(remoteRows, remoteKeys);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> local : localRows) {
            List<Object> localKey = localKeys.stream().map(local::get).toList();
            List<Map<String, Object>> matches = localKey.stream().anyMatch(java.util.Objects::isNull)
                    ? null : index.get(localKey);
            if (matches == null || matches.isEmpty()) {
                Map<String, Object> joined = new LinkedHashMap<>(local);
                remoteFields.forEach(field -> joined.put(relationCode + "." + field, null));
                result.add(joined);
            } else {
                for (Map<String, Object> remote : matches) {
                    Map<String, Object> joined = new LinkedHashMap<>(local);
                    remoteFields.forEach(field -> joined.put(relationCode + "." + field, remote.get(field)));
                    result.add(joined);
                }
            }
            if (result.size() > maxRows) {
                throw new VirtualDataException("PLAN_BUDGET_EXCEEDED", "Hash Join 结果超过 maxScanRows 预算: " + relationCode);
            }
        }
        return result;
    }

    private Map<List<Object>, List<Map<String, Object>>> relationIndex(
            List<Map<String, Object>> remoteRows,
            List<String> remoteKeys
    ) {
        Map<List<Object>, List<Map<String, Object>>> index = new LinkedHashMap<>();
        remoteRows.forEach(row -> {
            List<Object> key = remoteKeys.stream().map(row::get).toList();
            if (key.stream().noneMatch(java.util.Objects::isNull)) {
                index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
            }
        });
        return index;
    }

    private int joinBudget(VirtualQueryRequest request) {
        Integer configured = request.getHints() == null ? null : request.getHints().getMaxScanRows();
        return configured == null ? 10000 : Math.max(1, Math.min(100000, configured));
    }

    private List<ResolvedRelation> resolveRelations(CatalogSnapshot localSnapshot, VirtualQueryRequest request) {
        Map<String, VirtualRelationRequest> declarations = new LinkedHashMap<>();
        Set<String> representedPublishedCodes = new LinkedHashSet<>();
        if (request.getRelations() != null) {
            for (VirtualRelationRequest relation : request.getRelations()) {
                if (relation == null) {
                    throw new VirtualDataException("RELATION_NOT_FOUND", "关系请求不能为空");
                }
                String alias = relationAlias(relation);
                validateRelationAlias(localSnapshot, alias);
                if (declarations.putIfAbsent(alias, relation) != null) {
                    throw new VirtualDataException("RELATION_ALIAS_INVALID", "关系别名重复: " + alias);
                }
                if (hasText(relation.getRelationCode())) {
                    representedPublishedCodes.add(relation.getRelationCode().trim());
                }
            }
        }
        if (request.getRelationCodes() != null) {
            for (String relationCode : request.getRelationCodes()) {
                if (!hasText(relationCode)) continue;
                String code = relationCode.trim();
                if (declarations.containsKey(code) || representedPublishedCodes.contains(code)) continue;
                validateRelationAlias(localSnapshot, code);
                VirtualRelationRequest legacy = new VirtualRelationRequest();
                legacy.setKey(code);
                legacy.setRelationCode(code);
                declarations.put(code, legacy);
            }
        }
        List<ResolvedRelation> result = new ArrayList<>(declarations.size());
        declarations.forEach((alias, declaration) -> result.add(
                hasText(declaration.getRelationCode())
                        ? resolvePublishedRelation(localSnapshot, alias, declaration)
                        : resolveAdHocRelation(localSnapshot, alias, declaration)
        ));
        return List.copyOf(result);
    }

    private ResolvedRelation resolvePublishedRelation(
            CatalogSnapshot localSnapshot,
            String alias,
            VirtualRelationRequest request
    ) {
        String relationCode = request.getRelationCode().trim();
        Map<PublishedRelationKey, List<CatalogSnapshot.Relation>> grouped = new LinkedHashMap<>();
        for (CatalogSnapshot.Relation relation : localSnapshot.relationGroup(relationCode)) {
            boolean forward = localSnapshot.entityId().equals(relation.sourceEntityId());
            if (!forward && !localSnapshot.entityId().equals(relation.targetEntityId())) continue;
            Long remoteEntityId = forward ? relation.targetEntityId() : relation.sourceEntityId();
            grouped.computeIfAbsent(new PublishedRelationKey(forward, remoteEntityId), ignored -> new ArrayList<>())
                    .add(relation);
        }
        List<PublishedRelationCandidate> candidates = new ArrayList<>();
        for (Map.Entry<PublishedRelationKey, List<CatalogSnapshot.Relation>> entry : grouped.entrySet()) {
            CatalogSnapshot remoteSnapshot = catalogService.requirePublished(entry.getKey().remoteEntityId());
            if (!hasText(request.getTargetEntityCode())
                    || request.getTargetEntityCode().trim().equals(remoteSnapshot.entityCode())) {
                candidates.add(new PublishedRelationCandidate(
                        entry.getKey().forward(), remoteSnapshot, List.copyOf(entry.getValue())));
            }
        }
        if (candidates.isEmpty()) {
            throw new VirtualDataException("RELATION_NOT_FOUND",
                    "已发布虚拟关系不存在或目标实体不匹配: " + relationCode);
        }
        if (candidates.size() > 1) {
            throw new VirtualDataException("RELATION_AMBIGUOUS",
                    "关系编码对应多个目标实体，请提供 targetEntityCode: " + relationCode);
        }
        PublishedRelationCandidate candidate = candidates.get(0);
        List<String> localKeys = new ArrayList<>();
        List<String> remoteKeys = new ArrayList<>();
        for (CatalogSnapshot.Relation mapping : candidate.mappings()) {
            CatalogSnapshot.VirtualField localField = requireJoinField(
                    localSnapshot,
                    candidate.forward() ? mapping.sourceFieldId() : mapping.targetFieldId(),
                    relationCode
            );
            CatalogSnapshot.VirtualField remoteField = requireJoinField(
                    candidate.remoteSnapshot(),
                    candidate.forward() ? mapping.targetFieldId() : mapping.sourceFieldId(),
                    relationCode
            );
            requireCompatibleTypes(localField, remoteField, relationCode);
            localKeys.add(localField.code());
            remoteKeys.add(remoteField.code());
        }
        if (request.getLocalToRemoteFields() != null && !request.getLocalToRemoteFields().isEmpty()) {
            ResolvedJoinKeys requested = resolveJoinKeys(
                    localSnapshot, candidate.remoteSnapshot(), request.getLocalToRemoteFields(), relationCode);
            if (!fieldMapping(localKeys, remoteKeys).equals(requested.mapping())) {
                throw new VirtualDataException("CATALOG_RELATION_INVALID",
                        "请求 on 与已发布虚拟关系不一致: " + relationCode);
            }
        }
        RelationResultMode mode = publishedResultMode(
                candidate.mappings(), candidate.forward(), candidate.remoteSnapshot(), remoteKeys, relationCode);
        return new ResolvedRelation(alias, candidate.remoteSnapshot(), localKeys, remoteKeys, mode, request.getFilter());
    }

    private ResolvedRelation resolveAdHocRelation(
            CatalogSnapshot localSnapshot,
            String alias,
            VirtualRelationRequest request
    ) {
        if (!hasText(request.getTargetEntityCode())) {
            throw new VirtualDataException("RELATION_NOT_FOUND", "临时关系必须提供 targetEntityCode: " + alias);
        }
        if (request.getLocalToRemoteFields() == null || request.getLocalToRemoteFields().isEmpty()) {
            throw new VirtualDataException("RELATION_NOT_FOUND", "临时关系必须提供 on 字段映射: " + alias);
        }
        CatalogSnapshot remoteSnapshot = catalogService.requirePublished(request.getTargetEntityCode().trim(), null);
        ResolvedJoinKeys keys = resolveJoinKeys(
                localSnapshot, remoteSnapshot, request.getLocalToRemoteFields(), alias);
        RelationResultMode mode = request.getResultMode() == null
                ? inferResultMode(remoteSnapshot, keys.remoteKeys()) : request.getResultMode();
        return new ResolvedRelation(
                alias, remoteSnapshot, keys.localKeys(), keys.remoteKeys(), mode, request.getFilter());
    }

    private ResolvedJoinKeys resolveJoinKeys(
            CatalogSnapshot localSnapshot,
            CatalogSnapshot remoteSnapshot,
            Map<String, String> localToRemoteFields,
            String relationLabel
    ) {
        List<String> localKeys = new ArrayList<>();
        List<String> remoteKeys = new ArrayList<>();
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : localToRemoteFields.entrySet()) {
            String localCode = requireFieldCode(entry.getKey(), "本地 Join 字段", relationLabel);
            String remoteCode = requireFieldCode(entry.getValue(), "远端 Join 字段", relationLabel);
            CatalogSnapshot.VirtualField localField = requireJoinField(localSnapshot, localCode, relationLabel);
            CatalogSnapshot.VirtualField remoteField = requireJoinField(remoteSnapshot, remoteCode, relationLabel);
            requireCompatibleTypes(localField, remoteField, relationLabel);
            if (normalized.putIfAbsent(localCode, remoteCode) != null) {
                throw new VirtualDataException("CATALOG_RELATION_INVALID", "Join 本地字段重复: " + localCode);
            }
            localKeys.add(localCode);
            remoteKeys.add(remoteCode);
        }
        return new ResolvedJoinKeys(localKeys, remoteKeys, normalized);
    }

    private Set<String> localJoinFields(List<ResolvedRelation> relations) {
        Set<String> result = new LinkedHashSet<>();
        if (relations != null) relations.forEach(relation -> result.addAll(relation.localKeys()));
        return result;
    }

    private Map<String, RelationResultMode> relationModes(List<ResolvedRelation> relations) {
        Map<String, RelationResultMode> result = new LinkedHashMap<>();
        if (relations != null) relations.forEach(relation -> result.put(relation.alias(), relation.effectiveMode()));
        return result;
    }

    private RelationResultMode publishedResultMode(
            List<CatalogSnapshot.Relation> relations,
            boolean forward,
            CatalogSnapshot remoteSnapshot,
            List<String> remoteKeys,
            String relationCode
    ) {
        RelationResultMode mode = forward ? relations.get(0).resultMode() : relations.get(0).reverseResultMode();
        if (relations.stream().anyMatch(relation -> !Objects.equals(
                forward ? relation.resultMode() : relation.reverseResultMode(), mode))) {
            throw new VirtualDataException("CATALOG_RELATION_INVALID",
                    "同一虚拟关系的结果形态不一致: " + relationCode);
        }
        return mode == null ? inferResultMode(remoteSnapshot, remoteKeys) : mode;
    }

    private RelationResultMode inferResultMode(CatalogSnapshot remoteSnapshot, List<String> remoteKeys) {
        Set<String> primaryKeys = new LinkedHashSet<>();
        remoteSnapshot.fieldsByCode().values().stream()
                .filter(CatalogSnapshot.VirtualField::enabled)
                .filter(CatalogSnapshot.VirtualField::primaryKey)
                .map(CatalogSnapshot.VirtualField::code)
                .forEach(primaryKeys::add);
        return !primaryKeys.isEmpty() && new LinkedHashSet<>(remoteKeys).containsAll(primaryKeys)
                ? RelationResultMode.OBJECT : RelationResultMode.COLLECTION;
    }

    private List<String> requestedRelationFields(VirtualQueryRequest request, String relationCode, CatalogSnapshot remote) {
        String prefix = relationCode + ".";
        Set<String> requestedSet = new LinkedHashSet<>();
        if (request.getFields() != null) request.getFields().stream()
                .filter(field -> field.startsWith(prefix)).map(field -> field.substring(prefix.length())).forEach(requestedSet::add);
        collectRelationFilterFields(request.getFilter(), prefix, requestedSet);
        if (request.getSorts() != null) request.getSorts().forEach(sort -> addRelationField(sort.getField(), prefix, requestedSet));
        if (request.getAggregates() != null) request.getAggregates().forEach(
                aggregate -> addRelationField(aggregate.getField(), prefix, requestedSet));
        if (request.getGroupBy() != null) request.getGroupBy().forEach(field -> addRelationField(field, prefix, requestedSet));
        if (request.getGroupings() != null) request.getGroupings().forEach(
                grouping -> addRelationField(grouping.getField(), prefix, requestedSet));
        requestedSet.forEach(field -> requireJoinField(remote, field, relationCode));
        return List.copyOf(requestedSet);
    }

    private boolean hasRelationFilter(FilterNode node) {
        if (node == null) return false;
        if (node.getType() == FilterType.PREDICATE) return node.getField() != null && node.getField().contains(".");
        return node.getChildren() != null && node.getChildren().stream().anyMatch(this::hasRelationFilter);
    }

    private void collectLocalFilterFields(FilterNode node, Set<String> result) {
        if (node == null) return;
        if (node.getType() == FilterType.PREDICATE && node.getField() != null && !node.getField().contains(".")) result.add(node.getField());
        if (node.getChildren() != null) node.getChildren().forEach(child -> collectLocalFilterFields(child, result));
    }

    private void collectRelationFilterFields(FilterNode node, String prefix, Set<String> result) {
        if (node == null) return;
        if (node.getType() == FilterType.PREDICATE && node.getField() != null && node.getField().startsWith(prefix)) {
            result.add(node.getField().substring(prefix.length()));
        }
        if (node.getChildren() != null) node.getChildren().forEach(child -> collectRelationFilterFields(child, prefix, result));
    }

    private void addRelationField(String field, String prefix, Set<String> result) {
        if (field != null && field.startsWith(prefix)) result.add(field.substring(prefix.length()));
    }

    private void validateRelationReferences(VirtualQueryRequest request, List<String> relationCodes) {
        Set<String> referenced = new LinkedHashSet<>();
        if (request.getFields() != null) request.getFields().forEach(field -> collectRelationCode(field, referenced));
        if (request.getSorts() != null) request.getSorts().forEach(sort -> collectRelationCode(sort.getField(), referenced));
        if (request.getAggregates() != null) request.getAggregates().forEach(
                aggregate -> collectRelationCode(aggregate.getField(), referenced));
        if (request.getGroupBy() != null) request.getGroupBy().forEach(field -> collectRelationCode(field, referenced));
        if (request.getGroupings() != null) request.getGroupings().forEach(
                grouping -> collectRelationCode(grouping.getField(), referenced));
        collectRelationFilterCodes(request.getFilter(), referenced);
        Set<String> declared = new LinkedHashSet<>(relationCodes);
        referenced.removeAll(declared);
        if (!referenced.isEmpty()) {
            throw new VirtualDataException("RELATION_NOT_FOUND", "关联字段必须显式声明对应 relationCodes: " + referenced);
        }
    }

    /**
     * 集合关系可以作为明细投影或关系域过滤使用；将其当作标量进入全局条件、排序或聚合会产生不明确语义，必须显式拒绝。
     */
    private void validateCollectionRelationUsage(
            VirtualQueryRequest request,
            Map<String, RelationResultMode> relationModes
    ) {
        rejectCollectionRelationFilter(request.getFilter(), relationModes, "过滤");
        rejectCollectionRelationFilter(request.getHaving(), relationModes, "HAVING");
        if (request.getSorts() != null) {
            request.getSorts().forEach(sort -> rejectCollectionRelationField(sort.getField(), relationModes, "排序"));
        }
        if (request.getAggregates() != null) {
            request.getAggregates().forEach(aggregate -> rejectCollectionRelationField(
                    aggregate.getField(), relationModes, "聚合"));
        }
        if (request.getGroupBy() != null) {
            request.getGroupBy().forEach(field -> rejectCollectionRelationField(field, relationModes, "分组"));
        }
        if (request.getGroupings() != null) {
            request.getGroupings().forEach(grouping -> rejectCollectionRelationField(
                    grouping.getField(), relationModes, "分组"));
        }
    }

    private void rejectCollectionRelationFilter(
            FilterNode node,
            Map<String, RelationResultMode> relationModes,
            String usage
    ) {
        if (node == null) return;
        if (node.getType() == FilterType.PREDICATE) {
            rejectCollectionRelationField(node.getField(), relationModes, usage);
        }
        if (node.getChildren() != null) {
            node.getChildren().forEach(child -> rejectCollectionRelationFilter(child, relationModes, usage));
        }
    }

    private void rejectCollectionRelationField(
            String field,
            Map<String, RelationResultMode> relationModes,
            String usage
    ) {
        if (field == null) return;
        int separator = field.indexOf('.');
        if (separator <= 0) return;
        String relationCode = field.substring(0, separator);
        if (relationModes.get(relationCode) == RelationResultMode.COLLECTION) {
            throw new VirtualDataException("RELATION_COLLECTION_OPERATION_UNSUPPORTED",
                    "集合关系不能作为" + usage + "标量字段: " + field);
        }
    }

    private void collectRelationFilterCodes(FilterNode node, Set<String> result) {
        if (node == null) return;
        if (node.getType() == FilterType.PREDICATE) collectRelationCode(node.getField(), result);
        if (node.getChildren() != null) node.getChildren().forEach(child -> collectRelationFilterCodes(child, result));
    }

    private void collectRelationCode(String field, Set<String> result) {
        if (field == null) return;
        int dot = field.indexOf('.');
        if (dot > 0) result.add(field.substring(0, dot));
    }

    private String relationAlias(VirtualRelationRequest relation) {
        String alias = hasText(relation.getKey()) ? relation.getKey() : relation.getRelationCode();
        if (!hasText(alias)) {
            throw new VirtualDataException("RELATION_ALIAS_INVALID", "关系请求缺少 key/relationCode");
        }
        return alias.trim();
    }

    private void validateRelationAlias(CatalogSnapshot localSnapshot, String alias) {
        if (!alias.matches("[A-Za-z0-9_]+")) {
            throw new VirtualDataException("RELATION_ALIAS_INVALID", "关系别名格式非法: " + alias);
        }
        if (localSnapshot.fieldsByCode().containsKey(alias)) {
            throw new VirtualDataException("RELATION_ALIAS_INVALID", "关系别名不能与主实体字段重名: " + alias);
        }
    }

    private String requireFieldCode(String value, String label, String relationLabel) {
        if (!hasText(value) || !value.trim().matches("[A-Za-z0-9_]+")) {
            throw new VirtualDataException("FIELD_NOT_FOUND", label + "格式非法: " + relationLabel + "." + value);
        }
        return value.trim();
    }

    private CatalogSnapshot.VirtualField requireJoinField(
            CatalogSnapshot snapshot,
            Long fieldId,
            String relationLabel
    ) {
        CatalogSnapshot.VirtualField field = snapshot.fieldsById().get(fieldId);
        if (field == null || !field.enabled()) {
            throw new VirtualDataException("FIELD_NOT_FOUND",
                    "关系引用字段不存在或未启用: " + relationLabel + "." + fieldId);
        }
        return field;
    }

    private CatalogSnapshot.VirtualField requireJoinField(
            CatalogSnapshot snapshot,
            String fieldCode,
            String relationLabel
    ) {
        CatalogSnapshot.VirtualField field = snapshot.fieldsByCode().get(fieldCode);
        if (field == null || !field.enabled()) {
            throw new VirtualDataException("FIELD_NOT_FOUND",
                    "关系引用字段不存在或未启用: " + relationLabel + "." + fieldCode);
        }
        return field;
    }

    private void requireCompatibleTypes(
            CatalogSnapshot.VirtualField local,
            CatalogSnapshot.VirtualField remote,
            String relationLabel
    ) {
        if (local.logicalType() != remote.logicalType()) {
            throw new VirtualDataException("CATALOG_RELATION_INVALID",
                    "关系字段逻辑类型不兼容: " + relationLabel + "[" + local.code() + " -> " + remote.code() + "]");
        }
    }

    private Map<String, String> fieldMapping(List<String> localKeys, List<String> remoteKeys) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < localKeys.size(); index++) {
            if (result.putIfAbsent(localKeys.get(index), remoteKeys.get(index)) != null) {
                throw new VirtualDataException("CATALOG_RELATION_INVALID",
                        "已发布关系存在重复本地字段: " + localKeys.get(index));
            }
        }
        return result;
    }

    private VirtualQueryRequest copy(VirtualQueryRequest source) {
        VirtualQueryRequest target = new VirtualQueryRequest();
        target.setEntityCode(source.getEntityCode());
        target.setCatalogVersion(source.getCatalogVersion());
        target.setQueryType(source.getQueryType());
        target.setFields(source.getFields());
        target.setFilter(source.getFilter());
        target.setRelationCodes(source.getRelationCodes());
        target.setRelations(source.getRelations());
        target.setAggregates(source.getAggregates());
        target.setGroupBy(source.getGroupBy());
        target.setGroupings(source.getGroupings());
        target.setHaving(source.getHaving());
        target.setSorts(source.getSorts());
        target.setPage(source.getPage());
        target.setExactTotal(source.getExactTotal());
        target.setTraceLabel(source.getTraceLabel());
        target.setConsistency(source.getConsistency());
        target.setHints(source.getHints());
        return target;
    }

    private RawQueryResult exactCount(
            CatalogSnapshot snapshot,
            VirtualQueryRequest request,
            Map<String, RelationResultMode> relationModes,
            boolean relationFilter
    ) {
        if (!Boolean.TRUE.equals(request.getExactTotal())
                || request.getQueryType() != QueryType.LIST
                || relationModes.containsValue(RelationResultMode.OBJECT)
                || relationFilter) {
            return null;
        }
        VirtualQueryRequest count = copy(request);
        count.setQueryType(QueryType.COUNT);
        count.setFields(List.of());
        count.setRelationCodes(List.of());
        count.setRelations(List.of());
        count.setAggregates(List.of());
        count.setGroupBy(List.of());
        count.setGroupings(List.of());
        count.setHaving(null);
        count.setSorts(List.of());
        count.setExactTotal(false);
        return raw(snapshot, count, Set.of());
    }

    private VirtualQueryRequest normalizedRelations(VirtualQueryRequest request, List<String> relationCodes) {
        if (request.getRelationCodes() != null && request.getRelationCodes().equals(relationCodes)) {
            return request;
        }
        VirtualQueryRequest normalized = copy(request);
        normalized.setRelationCodes(relationCodes);
        return normalized;
    }

    private void validateRelationFilterScope(FilterNode node, String relationCode) {
        if (node == null) return;
        if (node.getType() == FilterType.PREDICATE
                && (node.getField() == null || node.getField().contains("."))) {
            throw new VirtualDataException(
                    "RELATION_FILTER_SCOPE_INVALID",
                    "关系域过滤只能引用目标虚拟实体自身字段: " + relationCode
            );
        }
        if (node.getChildren() != null) {
            node.getChildren().forEach(child -> validateRelationFilterScope(child, relationCode));
        }
    }

    private void addStableIdentityFields(CatalogSnapshot snapshot, Set<String> requiredFields) {
        List<String> primaryKeys = snapshot.fieldsByCode().values().stream()
                .filter(CatalogSnapshot.VirtualField::enabled)
                .filter(CatalogSnapshot.VirtualField::primaryKey)
                .map(CatalogSnapshot.VirtualField::code)
                .toList();
        if (primaryKeys.isEmpty()) {
            throw new VirtualDataException(
                    "PLAN_EXACTNESS_UNPROVABLE",
                    "关系查询需要虚拟主键以校验主实体唯一性: " + snapshot.entityCode()
            );
        }
        requiredFields.addAll(primaryKeys);
    }

    private void requireSingleValuedRelations(CatalogSnapshot snapshot, List<Map<String, Object>> rows) {
        List<String> primaryKeys = snapshot.fieldsByCode().values().stream()
                .filter(CatalogSnapshot.VirtualField::enabled)
                .filter(CatalogSnapshot.VirtualField::primaryKey)
                .map(CatalogSnapshot.VirtualField::code)
                .toList();
        Set<List<Object>> identities = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            List<Object> identity = new ArrayList<>();
            primaryKeys.forEach(field -> identity.add(row.get(field)));
            if (identity.stream().anyMatch(java.util.Objects::isNull)) {
                throw new VirtualDataException("PLAN_EXACTNESS_UNPROVABLE", "关系查询结果缺少稳定主实体标识");
            }
            if (!identities.add(List.copyOf(identity))) {
                throw new VirtualDataException(
                        "RELATION_CARDINALITY_UNSUPPORTED",
                        "DbQuery v1 明细关系仅支持 1:1 或 M:1，检测到主实体被关系复制: " + identity
                );
            }
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasScopedRelationFilters(VirtualQueryRequest request) {
        if (request.getRelations() == null) return false;
        for (VirtualRelationRequest relation : request.getRelations()) {
            if (relation != null && relation.getFilter() != null) return true;
        }
        return false;
    }

    private void requireRequest(VirtualQueryRequest request) {
        if (request == null || request.getEntityCode() == null || request.getEntityCode().isBlank()) {
            throw new VirtualDataException("CATALOG_NOT_FOUND", "entityCode 不能为空");
        }
    }

    private record RawQueryResult(PhysicalExecutionPlan plan, PhysicalExecutionEngine.ExecutionRows executionRows) {
    }

    private record RelationJoinResult(List<Map<String, Object>> rows, int physicalTaskCount, long executionMs) {
    }

    private record ResolvedRelation(
            String alias,
            CatalogSnapshot remoteSnapshot,
            List<String> localKeys,
            List<String> remoteKeys,
            RelationResultMode effectiveMode,
            FilterNode filter
    ) {
        private ResolvedRelation {
            localKeys = List.copyOf(localKeys);
            remoteKeys = List.copyOf(remoteKeys);
        }
    }

    private record ResolvedJoinKeys(
            List<String> localKeys,
            List<String> remoteKeys,
            Map<String, String> mapping
    ) {
        private ResolvedJoinKeys {
            localKeys = List.copyOf(localKeys);
            remoteKeys = List.copyOf(remoteKeys);
            mapping = Map.copyOf(new LinkedHashMap<>(mapping));
        }
    }

    private record PublishedRelationKey(boolean forward, Long remoteEntityId) {
    }

    private record PublishedRelationCandidate(
            boolean forward,
            CatalogSnapshot remoteSnapshot,
            List<CatalogSnapshot.Relation> mappings
    ) {
        private PublishedRelationCandidate {
            mappings = List.copyOf(mappings);
        }
    }
}
