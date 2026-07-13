package ai.platform.aiassit.data.virtualization.core.plan;

import ai.platform.aiassit.data.virtualization.api.dto.VirtualAggregate;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualSort;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.AggregateFunction;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.LogicalType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.QueryType;
import ai.platform.aiassit.data.virtualization.core.catalog.CatalogSnapshot;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    private CatalogSnapshot snapshot() {
        CatalogSnapshot.VirtualField id = new CatalogSnapshot.VirtualField(
                1L, "id", "ID", LogicalType.LONG, false, true, 0, true);
        return new CatalogSnapshot(1L, "order", "Order", CatalogStatus.PUBLISHED, 1, true,
                Map.of("id", id), Map.of(1L, id), List.of(), Map.of(), List.of());
    }
}
