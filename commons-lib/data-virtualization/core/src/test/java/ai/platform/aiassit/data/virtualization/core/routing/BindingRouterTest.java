package ai.platform.aiassit.data.virtualization.core.routing;

import ai.platform.aiassit.data.virtualization.api.config.BindingRoutingConfig;
import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualPage;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.BindingRole;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.ConsistencyLevel;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.QueryType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.RoutingStrategy;
import ai.platform.aiassit.data.virtualization.core.catalog.CatalogSnapshot;
import ai.platform.aiassit.data.virtualization.core.plan.VirtualLogicalPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BindingRouterTest {
    private final BindingRouter router = new BindingRouter();

    @Test
    void shouldPruneHashShardAndSelectReplicaForEventualRead() {
        CatalogSnapshot.Binding primary0 = binding(1L, "p0", "g0", BindingRole.PRIMARY, 0, 10);
        CatalogSnapshot.Binding replica0 = binding(2L, "r0", "g0", BindingRole.REPLICA, 0, 100);
        CatalogSnapshot.Binding primary1 = binding(3L, "p1", "g1", BindingRole.PRIMARY, 1, 10);
        CatalogSnapshot snapshot = new CatalogSnapshot(1L, "order", "Order", CatalogStatus.PUBLISHED, 1, true,
                Map.of(), Map.of(), List.of(primary0, replica0, primary1), Map.of(), List.of());
        FilterNode filter = new FilterNode();
        filter.setType(FilterType.PREDICATE);
        filter.setField("tenantId");
        filter.setOperator(FilterOperator.EQ);
        filter.setValue(2);
        VirtualLogicalPlan plan = new VirtualLogicalPlan("order", 1, QueryType.LIST, List.of(), Set.of(), filter,
                List.of(), List.of(), List.of(), List.of(), new VirtualPage(), ConsistencyLevel.EVENTUAL,
                16, 1000, 30000, true);

        List<BindingRouter.RoutingDecision> decisions = router.route(snapshot, plan);

        assertEquals(1, decisions.size());
        assertEquals("r0", decisions.get(0).binding().code());
    }

    @Test
    void shouldRouteHashInToOnlyMatchingShards() {
        CatalogSnapshot snapshot = snapshot(List.of(
                binding(1L, "p0", "g0", BindingRole.PRIMARY, 0, 10),
                binding(2L, "p1", "g1", BindingRole.PRIMARY, 1, 10)
        ));
        FilterNode filter = predicate("tenantId", FilterOperator.IN, null);
        filter.setValues(List.of(1, 2));

        List<BindingRouter.RoutingDecision> decisions = router.route(snapshot, plan(filter));

        assertEquals(Set.of("p0", "p1"), decisions.stream().map(item -> item.binding().code()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void shouldRouteListAcrossEquivalentNumericTypes() {
        CatalogSnapshot.Binding first = listBinding(1L, "cn", "g0", List.of(86L));
        CatalogSnapshot.Binding second = listBinding(2L, "us", "g1", List.of(1L));
        FilterNode filter = predicate("countryCode", FilterOperator.EQ, 86);

        List<BindingRouter.RoutingDecision> decisions = router.route(snapshot(List.of(first, second)), plan(filter));

        assertEquals(List.of("cn"), decisions.stream().map(item -> item.binding().code()).toList());
    }

    @Test
    void shouldPruneNonIntersectingRangeShards() {
        CatalogSnapshot.Binding first = rangeBinding(1L, "old", "g0", 0, 100);
        CatalogSnapshot.Binding second = rangeBinding(2L, "current", "g1", 100, 200);
        CatalogSnapshot.Binding third = rangeBinding(3L, "future", "g2", 200, 300);
        FilterNode filter = predicate("sequence", FilterOperator.GTE, 150);

        List<BindingRouter.RoutingDecision> decisions = router.route(snapshot(List.of(first, second, third)), plan(filter));

        assertEquals(List.of("current", "future"), decisions.stream().map(item -> item.binding().code()).toList());
    }

    private CatalogSnapshot.Binding binding(Long id, String code, String group, BindingRole role, int remainder, int weight) {
        BindingRoutingConfig config = new BindingRoutingConfig();
        config.setStrategy(RoutingStrategy.HASH);
        config.setShardFields(List.of("tenantId"));
        BindingRoutingConfig.HashRouteConfig hash = new BindingRoutingConfig.HashRouteConfig();
        hash.setModulus(2);
        hash.setRemainder(remainder);
        config.setHash(hash);
        return new CatalogSnapshot.Binding(id, code, group, role, id, "source", "orders", true,
                role == BindingRole.PRIMARY, weight, 0, config, true);
    }

    private CatalogSnapshot.Binding listBinding(Long id, String code, String group, List<Object> values) {
        BindingRoutingConfig config = new BindingRoutingConfig();
        config.setStrategy(RoutingStrategy.LIST);
        config.setShardFields(List.of("countryCode"));
        BindingRoutingConfig.ListRouteConfig list = new BindingRoutingConfig.ListRouteConfig();
        list.setValues(values);
        config.setList(list);
        return new CatalogSnapshot.Binding(id, code, group, BindingRole.PRIMARY, id, "source", "orders",
                true, true, 100, 0, config, true);
    }

    private CatalogSnapshot.Binding rangeBinding(Long id, String code, String group, Object lower, Object upper) {
        BindingRoutingConfig config = new BindingRoutingConfig();
        config.setStrategy(RoutingStrategy.RANGE);
        config.setShardFields(List.of("sequence"));
        BindingRoutingConfig.RangeRouteConfig range = new BindingRoutingConfig.RangeRouteConfig();
        range.setLower(lower);
        range.setUpper(upper);
        config.setRange(range);
        return new CatalogSnapshot.Binding(id, code, group, BindingRole.PRIMARY, id, "source", "orders",
                true, true, 100, 0, config, true);
    }

    private CatalogSnapshot snapshot(List<CatalogSnapshot.Binding> bindings) {
        return new CatalogSnapshot(1L, "order", "Order", CatalogStatus.PUBLISHED, 1, true,
                Map.of(), Map.of(), bindings, Map.of(), List.of());
    }

    private VirtualLogicalPlan plan(FilterNode filter) {
        return new VirtualLogicalPlan("order", 1, QueryType.LIST, List.of(), Set.of(), filter,
                List.of(), List.of(), List.of(), List.of(), new VirtualPage(), ConsistencyLevel.STRONG,
                16, 1000, 30000, true);
    }

    private FilterNode predicate(String field, FilterOperator operator, Object value) {
        FilterNode filter = new FilterNode();
        filter.setType(FilterType.PREDICATE);
        filter.setField(field);
        filter.setOperator(operator);
        filter.setValue(value);
        return filter;
    }
}
