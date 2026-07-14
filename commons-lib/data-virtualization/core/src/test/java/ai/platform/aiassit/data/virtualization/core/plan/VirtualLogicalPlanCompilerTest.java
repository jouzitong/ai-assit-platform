package ai.platform.aiassit.data.virtualization.core.plan;

import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualAggregate;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualGroupBy;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualSort;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.AggregateFunction;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.LogicalType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.QueryType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.SortDirection;
import ai.platform.aiassit.data.virtualization.core.catalog.CatalogSnapshot;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VirtualLogicalPlanCompilerTest {
    private final VirtualLogicalPlanCompiler compiler = new VirtualLogicalPlanCompiler();

    @Test
    void shouldAllowDeclaredRelationFieldsForApplicationMerge() {
        VirtualQueryRequest request = new VirtualQueryRequest();
        request.setEntityCode("order");
        request.setFields(List.of("id", "customer.name"));
        VirtualSort sort = new VirtualSort();
        sort.setField("customer.name");
        request.setSorts(List.of(sort));

        assertDoesNotThrow(() -> compiler.compile(snapshot(), request));
    }

    @Test
    void shouldRejectAggregateWithoutFunction() {
        VirtualQueryRequest request = new VirtualQueryRequest();
        request.setEntityCode("order");
        request.setQueryType(QueryType.AGGREGATE);
        VirtualAggregate aggregate = new VirtualAggregate();
        aggregate.setField("id");
        request.setAggregates(List.of(aggregate));

        assertThrows(VirtualDataException.class, () -> compiler.compile(snapshot(), request));
    }

    @Test
    void shouldAcceptCountAllAggregate() {
        VirtualQueryRequest request = new VirtualQueryRequest();
        request.setEntityCode("order");
        request.setQueryType(QueryType.AGGREGATE);
        VirtualAggregate aggregate = new VirtualAggregate();
        aggregate.setFunction(AggregateFunction.COUNT);
        aggregate.setField("*");
        request.setAggregates(List.of(aggregate));

        assertDoesNotThrow(() -> compiler.compile(snapshot(), request));
    }

    @Test
    void shouldCompileGroupingAliasAndAllowHavingAndSortByAggregateAlias() {
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
        having.setValue(100);
        request.setHaving(having);

        VirtualSort sort = new VirtualSort();
        sort.setField("totalAmount");
        sort.setDirection(SortDirection.DESC);
        request.setSorts(List.of(sort));

        VirtualLogicalPlan plan = compiler.compile(snapshot(), request);

        assertEquals(List.of("region"), plan.groupBy());
        assertEquals(List.of("amount", "region"), plan.requiredFields().stream().sorted().toList());
        assertEquals("totalAmount", plan.sorts().get(0).getField());
    }

    @Test
    void shouldRejectHavingThatDoesNotReferenceOutputAlias() {
        VirtualQueryRequest request = new VirtualQueryRequest();
        request.setEntityCode("order");
        request.setQueryType(QueryType.AGGREGATE);

        VirtualAggregate aggregate = new VirtualAggregate();
        aggregate.setFunction(AggregateFunction.SUM);
        aggregate.setField("amount");
        aggregate.setAlias("totalAmount");
        request.setAggregates(List.of(aggregate));

        FilterNode having = new FilterNode();
        having.setType(FilterType.PREDICATE);
        having.setField("amount");
        having.setOperator(FilterOperator.GT);
        having.setValue(100);
        request.setHaving(having);

        VirtualDataException exception = assertThrows(
                VirtualDataException.class,
                () -> compiler.compile(snapshot(), request)
        );
        assertEquals("FIELD_NOT_FOUND", exception.getCode());
    }

    private CatalogSnapshot snapshot() {
        CatalogSnapshot.VirtualField id = new CatalogSnapshot.VirtualField(
                1L, "id", "ID", LogicalType.LONG, false, true, 0, true);
        CatalogSnapshot.VirtualField region = new CatalogSnapshot.VirtualField(
                2L, "region", "Region", LogicalType.STRING, false, false, 1, true);
        CatalogSnapshot.VirtualField amount = new CatalogSnapshot.VirtualField(
                3L, "amount", "Amount", LogicalType.DECIMAL, false, false, 2, true);
        return new CatalogSnapshot(1L, "order", "Order", CatalogStatus.PUBLISHED, 1, true,
                Map.of("id", id, "region", region, "amount", amount),
                Map.of(1L, id, 2L, region, 3L, amount), List.of(), Map.of(), List.of());
    }
}
