package ai.platform.aiassit.data.virtualization.core.plan;

import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualPage;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.BindingRole;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.ConsistencyLevel;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FieldSide;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.LogicalType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.QueryType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.TransformMode;
import ai.platform.aiassit.data.virtualization.core.catalog.CatalogSnapshot;
import ai.platform.aiassit.data.virtualization.core.catalog.DefaultFieldMappingResolver;
import ai.platform.aiassit.data.virtualization.core.routing.BindingRouter;
import ai.platform.aiassit.data.virtualization.spi.catalog.PhysicalCatalogPort;
import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilterOperator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PhysicalPlanGeneratorTest {

    @Test
    void shouldGenerateDatabaseIndependentParameterizedSpec() {
        CatalogSnapshot.VirtualField field = new CatalogSnapshot.VirtualField(
                10L, "tenantId", "Tenant", LogicalType.LONG, false, false, 0, true);
        CatalogSnapshot.Port physical = new CatalogSnapshot.Port(
                100L, FieldSide.PHYSICAL, "physical", null, 20L, "tenant_id", 0, false);
        CatalogSnapshot.Port virtual = new CatalogSnapshot.Port(
                101L, FieldSide.VIRTUAL, "virtual", 10L, null, null, 0, true);
        CatalogSnapshot.TransformRule rule = new CatalogSnapshot.TransformRule(
                30L, 40L, "tenant", "tenant", TransformMode.BIDIRECTIONAL,
                "identity", 1, "identity", 1, Map.of(), Map.of(), true,
                List.of(physical), List.of(virtual));
        CatalogSnapshot.Binding binding = new CatalogSnapshot.Binding(
                40L, "primary", "default", BindingRole.PRIMARY, 50L,
                "source", "orders", true, true, 100, 0, null, true);
        CatalogSnapshot snapshot = new CatalogSnapshot(
                1L, "order", "Order", CatalogStatus.PUBLISHED, 1, true,
                Map.of("tenantId", field), Map.of(10L, field), List.of(binding),
                Map.of(40L, List.of(rule)), List.of());

        FilterNode filter = new FilterNode();
        filter.setType(FilterType.PREDICATE);
        filter.setField("tenantId");
        filter.setOperator(FilterOperator.EQ);
        filter.setValue("x' OR 1=1 --");
        VirtualLogicalPlan logicalPlan = new VirtualLogicalPlan(
                "order", 1, QueryType.LIST, List.of("tenantId"), Set.of("tenantId"), filter,
                List.of(), List.of(), List.of(), List.of(), new VirtualPage(), ConsistencyLevel.STRONG,
                16, 1000, 30000, true);

        BindingRouter router = mock(BindingRouter.class);
        when(router.route(snapshot, logicalPlan)).thenReturn(List.of(new BindingRouter.RoutingDecision(binding, "single")));
        PhysicalExecutionPlan plan = new PhysicalPlanGenerator(router, new PhysicalFilterMapper(), mock(DefaultFieldMappingResolver.class))
                .generate(snapshot, logicalPlan);

        PhysicalExecutionPlan.PhysicalTask task = plan.tasks().get(0);
        assertEquals("orders", task.queryCommand().querySpec().table());
        assertEquals(1001, task.queryCommand().querySpec().limit());
        assertEquals("tenant_id", task.queryCommand().querySpec().projections().get(0).field());
        assertEquals("__p20", task.queryCommand().querySpec().projections().get(0).alias());
        assertNotNull(task.queryCommand().querySpec().filter());
        assertEquals(PhysicalFilterOperator.EQ, task.queryCommand().querySpec().filter().operator());
        assertEquals("x' OR 1=1 --", task.queryCommand().querySpec().filter().value());
        assertFalse(task.queryCommand().querySpec().countOnly());
    }

    @Test
    void shouldUseDefaultIdentityMappingWhenNoEnabledRuleMatches() {
        CatalogSnapshot.VirtualField field = new CatalogSnapshot.VirtualField(
                10L, "orderId", "Order ID", LogicalType.LONG, false, false, 0, true);
        CatalogSnapshot.Binding binding = new CatalogSnapshot.Binding(
                40L, "primary", "default", BindingRole.PRIMARY, 50L,
                "source", "orders", true, true, 100, 0, null, true);
        CatalogSnapshot snapshot = new CatalogSnapshot(
                1L, "order", "Order", CatalogStatus.PUBLISHED, 1, true,
                Map.of("orderId", field), Map.of(10L, field), List.of(binding), Map.of(), List.of());
        VirtualLogicalPlan logicalPlan = new VirtualLogicalPlan(
                "order", 1, QueryType.LIST, List.of("orderId"), Set.of("orderId"), null,
                List.of(), List.of(), List.of(), List.of(), new VirtualPage(), ConsistencyLevel.STRONG,
                16, 1000, 30000, true);
        BindingRouter router = mock(BindingRouter.class);
        when(router.route(snapshot, logicalPlan)).thenReturn(List.of(new BindingRouter.RoutingDecision(binding, "single")));
        PhysicalCatalogPort physicalCatalogPort = mock(PhysicalCatalogPort.class);
        when(physicalCatalogPort.fields(50L)).thenReturn(List.of(new ai.platform.aiassit.data.virtualization.spi.catalog.PhysicalFieldDefinition(
                20L, "source", "orders", "order_id", "", "BIGINT", false, false, null, 1, true
        )));

        PhysicalExecutionPlan plan = new PhysicalPlanGenerator(
                router, new PhysicalFilterMapper(), new DefaultFieldMappingResolver(physicalCatalogPort)
        ).generate(snapshot, logicalPlan);

        PhysicalExecutionPlan.PhysicalTask task = plan.tasks().get(0);
        assertEquals("order_id", task.queryCommand().querySpec().projections().get(0).field());
        assertEquals("identity", task.transformRules().get(0).readTransformerCode());
        assertEquals(10L, task.transformRules().get(0).virtualPorts().get(0).virtualFieldId());
    }
}
