package ai.platform.aiassit.data.virtualization.core.execution;

import ai.platform.aiassit.data.virtualization.api.dto.VirtualAggregate;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualGroupBy;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryResponse;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualSort;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.AggregateFunction;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.QueryType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.RelationResultMode;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.SortDirection;
import ai.platform.aiassit.data.virtualization.core.catalog.CatalogSnapshot;
import ai.platform.aiassit.data.virtualization.core.plan.PhysicalExecutionPlan;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class VirtualResultFinalizer {
    private final FilterEvaluator filterEvaluator;

    public VirtualResultFinalizer(FilterEvaluator filterEvaluator) {
        this.filterEvaluator = filterEvaluator;
    }

    public VirtualQueryResponse finish(
            VirtualQueryRequest request,
            PhysicalExecutionPlan plan,
            PhysicalExecutionEngine.ExecutionRows executionRows,
            List<Map<String, Object>> joinedRows
    ) {
        QueryType queryType = request.getQueryType() == null ? QueryType.LIST : request.getQueryType();
        List<Map<String, Object>> rows = new ArrayList<>(joinedRows);
        boolean aggregated = queryType == QueryType.AGGREGATE
                || request.getAggregates() != null && !request.getAggregates().isEmpty();
        List<VirtualGroupBy> groupings = groupings(request);
        if (aggregated) {
            rows = aggregate(rows, groupings, request.getAggregates());
            if (request.getHaving() != null) {
                rows = rows.stream().filter(row -> filterEvaluator.test(request.getHaving(), row)).toList();
                rows = new ArrayList<>(rows);
            }
        }
        sort(rows, request.getSorts());
        long total = queryType == QueryType.COUNT && joinedRows.isEmpty() ? executionRows.total() : rows.size();
        Map<String, Object> summary = aggregated && groupings.isEmpty() && !rows.isEmpty()
                ? new LinkedHashMap<>(rows.get(0)) : new LinkedHashMap<>();
        if (queryType == QueryType.COUNT) rows = new ArrayList<>();
        else if (queryType == QueryType.GET) rows = rows.isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of(rows.get(0)));
        else rows = page(rows, request);
        rows = project(rows, request, plan);

        VirtualQueryResponse response = new VirtualQueryResponse();
        response.setRequestId(java.util.UUID.randomUUID().toString());
        response.setPlanId(plan.planId());
        response.setCatalogVersion(plan.snapshot().catalogVersion());
        response.setRecords(rows);
        response.setTotal(total);
        response.setPhysicalTaskCount(executionRows.physicalTaskCount());
        response.setExecutionMs(executionRows.executionMs());
        response.setSummary(summary);
        return response;
    }

    private List<Map<String, Object>> aggregate(
            List<Map<String, Object>> rows,
            List<VirtualGroupBy> groups,
            List<VirtualAggregate> definitions
    ) {
        List<VirtualAggregate> aggregates = definitions == null ? List.of() : definitions;
        Map<List<Object>, List<Map<String, Object>>> buckets = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            List<Object> key = groups.stream().map(group -> row.get(group.getField())).toList();
            buckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        if (buckets.isEmpty() && groups.isEmpty()) buckets.put(List.of(), List.of());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<List<Object>, List<Map<String, Object>>> bucket : buckets.entrySet()) {
            Map<String, Object> output = new LinkedHashMap<>();
            for (int i = 0; i < groups.size(); i++) {
                VirtualGroupBy group = groups.get(i);
                String alias = group.getAlias() == null || group.getAlias().isBlank()
                        ? group.getField() : group.getAlias();
                output.put(alias, bucket.getKey().get(i));
            }
            for (VirtualAggregate aggregate : aggregates) {
                String alias = aggregate.getAlias() == null || aggregate.getAlias().isBlank()
                        ? aggregate.getFunction().name().toLowerCase() + "_" + aggregate.getField() : aggregate.getAlias();
                output.put(alias, aggregate(bucket.getValue(), aggregate));
            }
            result.add(output);
        }
        return result;
    }

    private List<VirtualGroupBy> groupings(VirtualQueryRequest request) {
        if (request.getGroupings() != null && !request.getGroupings().isEmpty()) {
            return request.getGroupings();
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

    private Object aggregate(List<Map<String, Object>> rows, VirtualAggregate aggregate) {
        AggregateFunction function = aggregate.getFunction();
        if (function == AggregateFunction.COUNT) return (long) rows.size();
        List<Object> values = rows.stream().map(row -> row.get(aggregate.getField())).filter(java.util.Objects::nonNull).toList();
        if (values.isEmpty()) return null;
        return switch (function) {
            case SUM -> values.stream().map(this::decimal).reduce(BigDecimal.ZERO, BigDecimal::add);
            case AVG -> values.stream().map(this::decimal).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(values.size()), MathContext.DECIMAL64);
            case MIN -> values.stream().min(this::compare).orElse(null);
            case MAX -> values.stream().max(this::compare).orElse(null);
            default -> (long) values.size();
        };
    }

    private void sort(List<Map<String, Object>> rows, List<VirtualSort> sorts) {
        if (sorts == null || sorts.isEmpty()) return;
        Comparator<Map<String, Object>> comparator = null;
        for (VirtualSort sort : sorts) {
            Comparator<Map<String, Object>> next = (left, right) -> compare(left.get(sort.getField()), right.get(sort.getField()));
            if (sort.getDirection() == SortDirection.DESC) next = next.reversed();
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        rows.sort(comparator);
    }

    private List<Map<String, Object>> page(List<Map<String, Object>> rows, VirtualQueryRequest request) {
        int number = request.getPage() == null || request.getPage().getNumber() == null ? 1 : request.getPage().getNumber();
        int size = request.getPage() == null || request.getPage().getSize() == null ? 20 : request.getPage().getSize();
        int from = Math.min(rows.size(), Math.max(0, (number - 1) * size));
        int to = Math.min(rows.size(), from + size);
        return new ArrayList<>(rows.subList(from, to));
    }

    private List<Map<String, Object>> project(List<Map<String, Object>> rows, VirtualQueryRequest request, PhysicalExecutionPlan plan) {
        if (request.getQueryType() == QueryType.AGGREGATE || request.getAggregates() != null && !request.getAggregates().isEmpty()) return rows;
        List<String> fields = request.getFields() == null || request.getFields().isEmpty()
                ? plan.logicalPlan().projections() : request.getFields();
        Set<String> collectionRelations = plan.snapshot().relations().stream()
                .filter(CatalogSnapshot.Relation::enabled)
                .filter(relation -> plan.snapshot().entityId().equals(relation.sourceEntityId()))
                .filter(relation -> relation.resultMode() == RelationResultMode.COLLECTION)
                .map(CatalogSnapshot.Relation::relationCode)
                .collect(java.util.stream.Collectors.toSet());
        return rows.stream().map(row -> {
            Map<String, Object> projected = new LinkedHashMap<>();
            fields.forEach(field -> projectField(projected, row, field, collectionRelations));
            return projected;
        }).toList();
    }

    private void projectField(
            Map<String, Object> projected,
            Map<String, Object> row,
            String field,
            Set<String> collectionRelations
    ) {
        if (field == null) return;
        int separator = field.indexOf('.');
        if (separator > 0 && collectionRelations.contains(field.substring(0, separator))) {
            String relationCode = field.substring(0, separator);
            projected.computeIfAbsent(relationCode, ignored -> copyCollection(row.get(relationCode)));
            return;
        }
        projected.put(field, row.get(field));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> copyCollection(Object source) {
        if (!(source instanceof List<?> rows)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map) {
                result.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return result;
    }

    private BigDecimal decimal(Object value) { return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value)); }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int compare(Object left, Object right) {
        if (left == null || right == null) return left == right ? 0 : left == null ? -1 : 1;
        if (left instanceof Number || right instanceof Number) return decimal(left).compareTo(decimal(right));
        if (left instanceof Comparable comparable && left.getClass().isInstance(right)) return comparable.compareTo(right);
        return String.valueOf(left).compareTo(String.valueOf(right));
    }
}
