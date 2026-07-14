package ai.platform.aiassit.data.virtualization.core.plan;

import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.dto.QueryHints;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualAggregate;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualGroupBy;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualPage;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualSort;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.AggregateFunction;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.ConsistencyLevel;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.QueryType;
import ai.platform.aiassit.data.virtualization.core.catalog.CatalogSnapshot;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class VirtualLogicalPlanCompiler {
    public VirtualLogicalPlan compile(CatalogSnapshot snapshot, VirtualQueryRequest request) {
        return compile(snapshot, request, Set.of());
    }

    public VirtualLogicalPlan compile(CatalogSnapshot snapshot, VirtualQueryRequest request, Set<String> additionalRequiredFields) {
        if (request == null) {
            throw new VirtualDataException("FIELD_NOT_FOUND", "查询请求不能为空");
        }
        QueryType queryType = request.getQueryType() == null ? QueryType.LIST : request.getQueryType();
        List<VirtualGroupBy> groupings = effectiveGroupings(request);
        List<String> groupBy = groupings.stream().map(VirtualGroupBy::getField).toList();
        boolean aggregateQuery = queryType == QueryType.AGGREGATE
                || request.getAggregates() != null && !request.getAggregates().isEmpty();
        validateAggregates(queryType, request.getAggregates());
        Set<String> aggregateAliases = validateAggregateAliases(groupings, request.getAggregates());
        validateHaving(request.getHaving(), aggregateAliases);
        List<String> projections = request.getFields() == null || request.getFields().isEmpty()
                ? (queryType == QueryType.COUNT || aggregateQuery ? List.of() : snapshot.fieldsByCode().values().stream().filter(CatalogSnapshot.VirtualField::enabled)
                    .sorted(java.util.Comparator.comparingInt(CatalogSnapshot.VirtualField::ordinalPosition))
                    .map(CatalogSnapshot.VirtualField::code).toList())
                : request.getFields().stream().filter(field -> !field.contains(".")).toList();
        Set<String> required = new LinkedHashSet<>(projections);
        required.addAll(additionalRequiredFields == null ? Set.of() : additionalRequiredFields);
        collectFilterFields(request.getFilter(), required);
        if (request.getAggregates() != null) request.getAggregates().forEach(item -> {
            if (item.getField() != null && !item.getField().equals("*") && !isRelationField(item.getField())) {
                required.add(item.getField());
            }
        });
        if (!aggregateQuery && request.getSorts() != null) request.getSorts().stream()
                .map(VirtualSort::getField).filter(field -> !isRelationField(field)).forEach(required::add);
        groupBy.stream()
                .filter(field -> !isRelationField(field)).forEach(required::add);
        required.forEach(field -> {
            CatalogSnapshot.VirtualField definition = snapshot.fieldsByCode().get(field);
            if (definition == null || !definition.enabled()) {
                throw new VirtualDataException("FIELD_NOT_FOUND", "虚拟字段不存在或未启用: " + field);
            }
        });
        validateFilter(request.getFilter());
        validateSorts(request.getSorts(), aggregateQuery, aggregateAliases);
        validateFieldReferences(request.getFields(), "投影");
        validateFieldReferences(groupBy, "分组");

        QueryHints hints = request.getHints() == null ? new QueryHints() : request.getHints();
        VirtualPage page = request.getPage() == null ? new VirtualPage() : request.getPage();
        page.setNumber(clamp(page.getNumber(), 1, Integer.MAX_VALUE, 1));
        page.setSize(clamp(page.getSize(), 1, 1000, 20));
        return new VirtualLogicalPlan(
                snapshot.entityCode(), snapshot.catalogVersion(), queryType, List.copyOf(projections), Set.copyOf(required),
                request.getFilter(), safe(request.getRelationCodes()), safe(request.getAggregates()), List.copyOf(groupBy),
                safe(request.getSorts()), page, request.getConsistency() == null ? ConsistencyLevel.STRONG : request.getConsistency(),
                clamp(hints.getMaxPhysicalTasks(), 1, 64, 16), clamp(hints.getMaxScanRows(), 1, 100000, 10000),
                clamp(hints.getTimeoutMs(), 100, 120000, 30000), !Boolean.FALSE.equals(hints.getAllowLocalTransform())
        );
    }

    private void collectFilterFields(FilterNode node, Set<String> fields) {
        if (node == null) return;
        if (node.getType() == FilterType.PREDICATE && node.getField() != null && !isRelationField(node.getField())) {
            fields.add(node.getField());
        }
        if (node.getChildren() != null) node.getChildren().forEach(child -> collectFilterFields(child, fields));
    }

    private void validateFilter(FilterNode node) {
        if (node == null) return;
        if (node.getType() == null) throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "过滤节点缺少 type");
        if (node.getType() == FilterType.PREDICATE && (node.getField() == null || node.getOperator() == null)) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "过滤谓词缺少 field/operator");
        }
        if (node.getType() == FilterType.PREDICATE) validateFieldReference(node.getField(), "过滤");
        if (node.getType() != FilterType.PREDICATE && (node.getChildren() == null || node.getChildren().isEmpty())) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "逻辑过滤节点缺少 children");
        }
        if (node.getType() == FilterType.NOT && node.getChildren() != null && node.getChildren().size() != 1) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "NOT 过滤节点必须且只能有一个 child");
        }
        if (node.getChildren() != null) node.getChildren().forEach(this::validateFilter);
    }

    private void validateSorts(List<VirtualSort> sorts, boolean aggregateQuery, Set<String> aggregateAliases) {
        if (sorts == null) return;
        for (VirtualSort sort : sorts) {
            if (sort == null || sort.getField() == null || sort.getField().isBlank() || sort.getDirection() == null) {
                throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "排序定义缺少 field/direction");
            }
            if (aggregateQuery) {
                if (!aggregateAliases.contains(sort.getField())) {
                    throw new VirtualDataException("FIELD_NOT_FOUND", "聚合排序只能引用分组或聚合别名: " + sort.getField());
                }
            } else {
                validateFieldReference(sort.getField(), "排序");
            }
        }
    }

    private List<VirtualGroupBy> effectiveGroupings(VirtualQueryRequest request) {
        if (request.getGroupings() != null && !request.getGroupings().isEmpty()) {
            return List.copyOf(request.getGroupings());
        }
        if (request.getGroupBy() == null || request.getGroupBy().isEmpty()) {
            return List.of();
        }
        return request.getGroupBy().stream().map(field -> {
            VirtualGroupBy group = new VirtualGroupBy();
            group.setField(field);
            group.setAlias(field);
            return group;
        }).toList();
    }

    private Set<String> validateAggregateAliases(
            List<VirtualGroupBy> groupings,
            List<VirtualAggregate> aggregates
    ) {
        Set<String> aliases = new LinkedHashSet<>();
        for (VirtualGroupBy group : groupings) {
            if (group == null) {
                throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "分组定义不能为空");
            }
            validateFieldReference(group.getField(), "分组");
            String alias = group.getAlias() == null || group.getAlias().isBlank() ? group.getField() : group.getAlias();
            if (!aliases.add(alias)) {
                throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "分组或聚合别名重复: " + alias);
            }
        }
        if (aggregates != null) {
            for (VirtualAggregate aggregate : aggregates) {
                if (aggregate == null || aggregate.getFunction() == null) continue;
                String alias = aggregate.getAlias();
                if (alias == null || alias.isBlank()) {
                    String field = aggregate.getField() == null || aggregate.getField().isBlank() ? "all" : aggregate.getField();
                    alias = aggregate.getFunction().name().toLowerCase() + "_" + field;
                }
                if (!aliases.add(alias)) {
                    throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "分组或聚合别名重复: " + alias);
                }
            }
        }
        return aliases;
    }

    private void validateHaving(FilterNode node, Set<String> aliases) {
        if (node == null) return;
        validateFilter(node);
        validateHavingFields(node, aliases);
    }

    private void validateHavingFields(FilterNode node, Set<String> aliases) {
        if (node.getType() == FilterType.PREDICATE && !aliases.contains(node.getField())) {
            throw new VirtualDataException("FIELD_NOT_FOUND", "HAVING 只能引用分组或聚合别名: " + node.getField());
        }
        if (node.getChildren() != null) {
            node.getChildren().forEach(child -> validateHavingFields(child, aliases));
        }
    }

    private void validateAggregates(QueryType queryType, List<VirtualAggregate> aggregates) {
        if (queryType == QueryType.AGGREGATE && (aggregates == null || aggregates.isEmpty())) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "AGGREGATE 查询至少需要一个聚合定义");
        }
        if (aggregates == null) return;
        for (VirtualAggregate aggregate : aggregates) {
            if (aggregate == null || aggregate.getFunction() == null) {
                throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "聚合定义缺少 function");
            }
            String field = aggregate.getField();
            if (aggregate.getFunction() == AggregateFunction.COUNT && (field == null || field.isBlank())) {
                aggregate.setField("*");
                continue;
            }
            if (field == null || field.isBlank() || aggregate.getFunction() != AggregateFunction.COUNT && "*".equals(field)) {
                throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "聚合函数缺少有效 field: " + aggregate.getFunction());
            }
            if (!"*".equals(field)) validateFieldReference(field, "聚合");
        }
    }

    private void validateFieldReferences(List<String> fields, String usage) {
        if (fields == null) return;
        fields.forEach(field -> validateFieldReference(field, usage));
    }

    private void validateFieldReference(String field, String usage) {
        if (field == null || field.isBlank()) {
            throw new VirtualDataException("FIELD_NOT_FOUND", usage + "字段编码不能为空");
        }
        if (isRelationField(field)) {
            String[] parts = field.split("\\.", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new VirtualDataException("FIELD_NOT_FOUND", usage + "关联字段格式必须为 relationCode.fieldCode: " + field);
            }
        }
    }

    private boolean isRelationField(String field) {
        return field != null && field.contains(".");
    }

    private int clamp(Integer value, int min, int max, int defaultValue) {
        if (value == null) return defaultValue;
        return Math.max(min, Math.min(max, value));
    }

    private <T> List<T> safe(List<T> source) {
        return source == null ? new ArrayList<>() : List.copyOf(source);
    }
}
