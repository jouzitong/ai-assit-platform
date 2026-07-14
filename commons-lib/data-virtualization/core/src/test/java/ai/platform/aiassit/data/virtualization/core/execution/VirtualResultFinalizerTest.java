package ai.platform.aiassit.data.virtualization.core.execution;

import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualAggregate;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualGroupBy;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualPage;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryResponse;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualSort;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.AggregateFunction;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.ConsistencyLevel;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.LogicalType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.QueryType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.SortDirection;
import ai.platform.aiassit.data.virtualization.core.catalog.CatalogSnapshot;
import ai.platform.aiassit.data.virtualization.core.plan.PhysicalExecutionPlan;
import ai.platform.aiassit.data.virtualization.core.plan.VirtualLogicalPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualResultFinalizerTest {

    private final VirtualResultFinalizer finalizer = new VirtualResultFinalizer(new FilterEvaluator());

    @Test
    void shouldUseGroupingAliasApplyHavingSortByAggregateAliasAndCountBeforePaging() {
        VirtualQueryRequest request = aggregateRequest();
        VirtualPage page = new VirtualPage();
        page.setNumber(1);
        page.setSize(1);
        request.setPage(page);

        List<Map<String, Object>> source = List.of(
                Map.of("region", "A", "amount", 7),
                Map.of("region", "A", "amount", 5),
                Map.of("region", "B", "amount", 30),
                Map.of("region", "C", "amount", 5)
        );

        VirtualQueryResponse response = finalizer.finish(
                request,
                plan(request),
                new PhysicalExecutionEngine.ExecutionRows(source, source.size(), 2, 7),
                source
        );

        assertEquals(2L, response.getTotal());
        assertEquals(1, response.getRecords().size());
        assertEquals("B", response.getRecords().get(0).get("regionName"));
        assertEquals(new BigDecimal("30"), response.getRecords().get(0).get("totalAmount"));
        assertTrue(response.getSummary().isEmpty());
    }

    @Test
    void shouldExposeSingleUngroupedAggregateAsSummary() {
        VirtualQueryRequest request = new VirtualQueryRequest();
        request.setEntityCode("order");
        request.setQueryType(QueryType.AGGREGATE);

        VirtualAggregate count = new VirtualAggregate();
        count.setFunction(AggregateFunction.COUNT);
        count.setField("*");
        count.setAlias("count");
        request.setAggregates(List.of(count));

        List<Map<String, Object>> source = List.of(Map.of("id", 1L), Map.of("id", 2L));
        VirtualQueryResponse response = finalizer.finish(
                request,
                plan(request),
                new PhysicalExecutionEngine.ExecutionRows(source, source.size(), 1, 3),
                source
        );

        assertEquals(1L, response.getTotal());
        assertEquals(Map.of("count", 2L), response.getSummary());
        assertEquals(response.getSummary(), response.getRecords().get(0));
    }

    private VirtualQueryRequest aggregateRequest() {
        VirtualQueryRequest request = new VirtualQueryRequest();
        request.setEntityCode("order");
        request.setQueryType(QueryType.AGGREGATE);

        VirtualGroupBy grouping = new VirtualGroupBy();
        grouping.setField("region");
        grouping.setAlias("regionName");
        request.setGroupings(List.of(grouping));

        VirtualAggregate aggregate = new VirtualAggregate();
        aggregate.setFunction(AggregateFunction.SUM);
        aggregate.setField("amount");
        aggregate.setAlias("totalAmount");
        request.setAggregates(List.of(aggregate));

        FilterNode having = new FilterNode();
        having.setType(FilterType.PREDICATE);
        having.setField("totalAmount");
        having.setOperator(FilterOperator.GT);
        having.setValue(10);
        request.setHaving(having);

        VirtualSort sort = new VirtualSort();
        sort.setField("totalAmount");
        sort.setDirection(SortDirection.DESC);
        request.setSorts(List.of(sort));
        return request;
    }

    private PhysicalExecutionPlan plan(VirtualQueryRequest request) {
        List<String> groupBy = request.getGroupings().stream().map(VirtualGroupBy::getField).toList();
        VirtualLogicalPlan logicalPlan = new VirtualLogicalPlan(
                "order", 1, request.getQueryType(), List.of("id", "region", "amount"),
                Set.of("id", "region", "amount"), request.getFilter(), List.of(),
                request.getAggregates(), groupBy, request.getSorts(), request.getPage(),
                ConsistencyLevel.STRONG, 16, 10000, 30000, true
        );
        return new PhysicalExecutionPlan("plan-1", snapshot(), logicalPlan, List.of(), List.of());
    }

    private CatalogSnapshot snapshot() {
        CatalogSnapshot.VirtualField id = new CatalogSnapshot.VirtualField(
                1L, "id", "ID", LogicalType.LONG, false, true, 0, true);
        CatalogSnapshot.VirtualField region = new CatalogSnapshot.VirtualField(
                2L, "region", "Region", LogicalType.STRING, false, false, 1, true);
        CatalogSnapshot.VirtualField amount = new CatalogSnapshot.VirtualField(
                3L, "amount", "Amount", LogicalType.DECIMAL, false, false, 2, true);
        return new CatalogSnapshot(
                1L, "order", "Order", CatalogStatus.PUBLISHED, 1, true,
                Map.of("id", id, "region", region, "amount", amount),
                Map.of(1L, id, 2L, region, 3L, amount), List.of(), Map.of(), List.of()
        );
    }
}
