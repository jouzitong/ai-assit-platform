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
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicalSqlRendererTest {
    @Test
    void shouldRenderParameterizedIdentityFilter() {
        CatalogSnapshot.VirtualField field = new CatalogSnapshot.VirtualField(10L, "tenantId", "Tenant", LogicalType.LONG, false, false, 0, true);
        CatalogSnapshot.Port physical = new CatalogSnapshot.Port(100L, FieldSide.PHYSICAL, "physical", null, 20L, "tenant_id", 0, false);
        CatalogSnapshot.Port virtual = new CatalogSnapshot.Port(101L, FieldSide.VIRTUAL, "virtual", 10L, null, null, 0, true);
        CatalogSnapshot.TransformRule rule = new CatalogSnapshot.TransformRule(30L, 40L, "tenant", "tenant", TransformMode.BIDIRECTIONAL,
                "identity", 1, "identity", 1, Map.of(), Map.of(), true, List.of(physical), List.of(virtual));
        CatalogSnapshot.Binding binding = new CatalogSnapshot.Binding(40L, "primary", "default", BindingRole.PRIMARY, 50L,
                "source", "orders", true, true, 100, 0, null, true);
        CatalogSnapshot snapshot = new CatalogSnapshot(1L, "order", "Order", CatalogStatus.PUBLISHED, 1, true,
                Map.of("tenantId", field), Map.of(10L, field), List.of(binding), Map.of(40L, List.of(rule)), List.of());
        FilterNode filter = new FilterNode();
        filter.setType(FilterType.PREDICATE);
        filter.setField("tenantId");
        filter.setOperator(FilterOperator.EQ);
        filter.setValue("x' OR 1=1 --");
        VirtualLogicalPlan plan = new VirtualLogicalPlan("order", 1, QueryType.LIST, List.of("tenantId"), Set.of("tenantId"), filter,
                List.of(), List.of(), List.of(), List.of(), new VirtualPage(), ConsistencyLevel.STRONG,
                16, 1000, 30000, true);

        PhysicalSqlRenderer.Rendered rendered = new PhysicalSqlRenderer().render(snapshot, binding, DbAccessDbType.MYSQL,
                plan, List.of(rule), Map.of(20L, "__p20"));

        assertTrue(rendered.sql().contains("`tenant_id` = ?"));
        assertTrue(rendered.sql().contains("LIMIT 1001"));
        assertFalse(rendered.sql().contains("OR 1=1"));
        assertEquals(List.of("x' OR 1=1 --"), rendered.parameters());
    }
}
