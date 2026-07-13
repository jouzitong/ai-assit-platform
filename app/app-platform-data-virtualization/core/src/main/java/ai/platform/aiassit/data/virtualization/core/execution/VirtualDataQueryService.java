package ai.platform.aiassit.data.virtualization.core.execution;

import ai.platform.aiassit.data.virtualization.api.dto.QueryHints;
import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualExplainResponse;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualPage;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryResponse;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.QueryType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
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
import java.util.Set;

@Service
@Slf4j
public class VirtualDataQueryService {
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

    public VirtualQueryResponse query(VirtualQueryRequest request) {
        requireRequest(request);
        CatalogSnapshot snapshot = catalogService.requirePublished(request.getEntityCode(), request.getCatalogVersion());
        validateRelationReferences(request);
        Set<String> joinFields = localJoinFields(snapshot, request.getRelationCodes());
        boolean relationFilter = hasRelationFilter(request.getFilter());
        if (relationFilter && (request.getRelationCodes() == null || request.getRelationCodes().isEmpty())) {
            throw new VirtualDataException("RELATION_NOT_FOUND", "关联字段过滤必须显式声明 relationCodes");
        }
        if (relationFilter) {
            collectLocalFilterFields(request.getFilter(), joinFields);
            logicalPlanCompiler.compile(snapshot, request, joinFields);
        }
        VirtualQueryRequest executionRequest = request;
        if (relationFilter || request.getQueryType() == QueryType.COUNT && request.getRelationCodes() != null && !request.getRelationCodes().isEmpty()) {
            executionRequest = copy(request);
            executionRequest.setQueryType(QueryType.LIST);
            if (relationFilter) executionRequest.setFilter(null);
        }
        RawQueryResult raw = raw(snapshot, executionRequest, joinFields);
        List<Map<String, Object>> rows = joinRelations(snapshot, request, raw.executionRows().rows());
        if (relationFilter) rows = rows.stream().filter(row -> filterEvaluator.test(request.getFilter(), row)).toList();
        VirtualQueryResponse response = resultFinalizer.finish(request, raw.plan(), raw.executionRows(), rows);
        log.info("virtual data query completed: planId={}, entityCode={}, catalogVersion={}, tasks={}, rows={}, executionMs={}",
                response.getPlanId(), request.getEntityCode(), response.getCatalogVersion(),
                response.getPhysicalTaskCount(), response.getRecords().size(), response.getExecutionMs());
        return response;
    }

    public VirtualExplainResponse explain(VirtualQueryRequest request) {
        requireRequest(request);
        CatalogSnapshot snapshot = catalogService.requirePublished(request.getEntityCode(), request.getCatalogVersion());
        validateRelationReferences(request);
        Set<String> extraFields = localJoinFields(snapshot, request.getRelationCodes());
        boolean relationFilter = hasRelationFilter(request.getFilter());
        if (relationFilter) logicalPlanCompiler.compile(snapshot, request, extraFields);
        VirtualQueryRequest executionRequest = request;
        if (relationFilter || request.getQueryType() == QueryType.COUNT && request.getRelationCodes() != null && !request.getRelationCodes().isEmpty()) {
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
        if (request.getRelationCodes() != null && !request.getRelationCodes().isEmpty()) {
            response.getWarnings().add("关联关系将在应用层执行受预算保护的 Hash Join");
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

    private List<Map<String, Object>> joinRelations(
            CatalogSnapshot localSnapshot,
            VirtualQueryRequest request,
            List<Map<String, Object>> initialRows
    ) {
        if (request.getRelationCodes() == null || request.getRelationCodes().isEmpty()) return initialRows;
        List<Map<String, Object>> rows = initialRows;
        for (String relationCode : request.getRelationCodes()) {
            List<CatalogSnapshot.Relation> relations = localSnapshot.relationGroup(relationCode);
            if (relations.isEmpty()) throw new VirtualDataException("RELATION_NOT_FOUND", "虚拟关系不存在: " + relationCode);
            boolean forward = relations.get(0).sourceEntityId().equals(localSnapshot.entityId());
            Long remoteEntityId = forward ? relations.get(0).targetEntityId() : relations.get(0).sourceEntityId();
            CatalogSnapshot remoteSnapshot = catalogService.requirePublished(remoteEntityId);
            List<String> localKeys = relations.stream().map(relation -> fieldCode(localSnapshot,
                    forward ? relation.sourceFieldId() : relation.targetFieldId())).toList();
            List<String> remoteKeys = relations.stream().map(relation -> fieldCode(remoteSnapshot,
                    forward ? relation.targetFieldId() : relation.sourceFieldId())).toList();
            List<String> requestedRemote = requestedRelationFields(request, relationCode, remoteSnapshot);
            Set<String> remoteRequired = new LinkedHashSet<>(remoteKeys);
            remoteRequired.addAll(requestedRemote);

            VirtualQueryRequest remoteRequest = new VirtualQueryRequest();
            remoteRequest.setEntityCode(remoteSnapshot.entityCode());
            remoteRequest.setCatalogVersion(remoteSnapshot.catalogVersion());
            remoteRequest.setQueryType(QueryType.LIST);
            remoteRequest.setFields(new ArrayList<>(remoteRequired));
            remoteRequest.setConsistency(request.getConsistency());
            remoteRequest.setHints(request.getHints() == null ? new QueryHints() : request.getHints());
            remoteRequest.setPage(new VirtualPage());
            RawQueryResult remoteRaw = raw(remoteSnapshot, remoteRequest, Set.of());
            rows = hashJoin(rows, remoteRaw.executionRows().rows(), localKeys, remoteKeys, requestedRemote,
                    relationCode, joinBudget(request));
        }
        return rows;
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
        Map<List<Object>, List<Map<String, Object>>> index = new LinkedHashMap<>();
        remoteRows.forEach(row -> {
            List<Object> key = remoteKeys.stream().map(row::get).toList();
            if (key.stream().noneMatch(java.util.Objects::isNull)) {
                index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
            }
        });
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

    private int joinBudget(VirtualQueryRequest request) {
        Integer configured = request.getHints() == null ? null : request.getHints().getMaxScanRows();
        return configured == null ? 10000 : Math.max(1, Math.min(100000, configured));
    }

    private Set<String> localJoinFields(CatalogSnapshot snapshot, List<String> relationCodes) {
        Set<String> result = new LinkedHashSet<>();
        if (relationCodes == null) return result;
        for (String code : relationCodes) {
            List<CatalogSnapshot.Relation> relations = snapshot.relationGroup(code);
            if (relations.isEmpty()) throw new VirtualDataException("RELATION_NOT_FOUND", "虚拟关系不存在: " + code);
            for (CatalogSnapshot.Relation relation : relations) {
                Long fieldId = relation.sourceEntityId().equals(snapshot.entityId()) ? relation.sourceFieldId() : relation.targetFieldId();
                result.add(fieldCode(snapshot, fieldId));
            }
        }
        return result;
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
        List<String> requested = List.copyOf(requestedSet);
        if (!requested.isEmpty()) return requested;
        return remote.fieldsByCode().values().stream().filter(CatalogSnapshot.VirtualField::enabled)
                .sorted(java.util.Comparator.comparingInt(CatalogSnapshot.VirtualField::ordinalPosition))
                .map(CatalogSnapshot.VirtualField::code).toList();
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

    private void validateRelationReferences(VirtualQueryRequest request) {
        Set<String> referenced = new LinkedHashSet<>();
        if (request.getFields() != null) request.getFields().forEach(field -> collectRelationCode(field, referenced));
        if (request.getSorts() != null) request.getSorts().forEach(sort -> collectRelationCode(sort.getField(), referenced));
        if (request.getAggregates() != null) request.getAggregates().forEach(
                aggregate -> collectRelationCode(aggregate.getField(), referenced));
        if (request.getGroupBy() != null) request.getGroupBy().forEach(field -> collectRelationCode(field, referenced));
        collectRelationFilterCodes(request.getFilter(), referenced);
        Set<String> declared = request.getRelationCodes() == null
                ? Set.of() : new LinkedHashSet<>(request.getRelationCodes());
        referenced.removeAll(declared);
        if (!referenced.isEmpty()) {
            throw new VirtualDataException("RELATION_NOT_FOUND", "关联字段必须显式声明对应 relationCodes: " + referenced);
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

    private String fieldCode(CatalogSnapshot snapshot, Long fieldId) {
        CatalogSnapshot.VirtualField field = snapshot.fieldsById().get(fieldId);
        if (field == null) throw new VirtualDataException("FIELD_NOT_FOUND", "关系引用字段不属于目录: " + fieldId);
        return field.code();
    }

    private VirtualQueryRequest copy(VirtualQueryRequest source) {
        VirtualQueryRequest target = new VirtualQueryRequest();
        target.setEntityCode(source.getEntityCode());
        target.setCatalogVersion(source.getCatalogVersion());
        target.setQueryType(source.getQueryType());
        target.setFields(source.getFields());
        target.setFilter(source.getFilter());
        target.setRelationCodes(source.getRelationCodes());
        target.setAggregates(source.getAggregates());
        target.setGroupBy(source.getGroupBy());
        target.setSorts(source.getSorts());
        target.setPage(source.getPage());
        target.setConsistency(source.getConsistency());
        target.setHints(source.getHints());
        return target;
    }

    private void requireRequest(VirtualQueryRequest request) {
        if (request == null || request.getEntityCode() == null || request.getEntityCode().isBlank()) {
            throw new VirtualDataException("CATALOG_NOT_FOUND", "entityCode 不能为空");
        }
    }

    private record RawQueryResult(PhysicalExecutionPlan plan, PhysicalExecutionEngine.ExecutionRows executionRows) {
    }
}
