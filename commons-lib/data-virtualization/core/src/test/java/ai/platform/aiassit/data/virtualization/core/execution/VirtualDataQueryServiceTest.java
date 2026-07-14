package ai.platform.aiassit.data.virtualization.core.execution;

import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualPage;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryResponse;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualRelationRequest;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.ConsistencyLevel;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.LogicalType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.RelationResultMode;
import ai.platform.aiassit.data.virtualization.core.catalog.CatalogSnapshot;
import ai.platform.aiassit.data.virtualization.core.catalog.VirtualCatalogService;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.core.plan.PhysicalExecutionPlan;
import ai.platform.aiassit.data.virtualization.core.plan.PhysicalPlanGenerator;
import ai.platform.aiassit.data.virtualization.core.plan.VirtualLogicalPlan;
import ai.platform.aiassit.data.virtualization.core.plan.VirtualLogicalPlanCompiler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VirtualDataQueryServiceTest {

    @Test
    void shouldRunIndependentCountBranchForExactListTotal() {
        Fixture fixture = fixture(List.of());
        VirtualQueryRequest request = new VirtualQueryRequest();
        request.setEntityCode("order");
        request.setCatalogVersion(1L);
        request.setFields(List.of("id"));
        request.setExactTotal(true);

        VirtualQueryResponse response = fixture.service().query(request);

        assertEquals(1L, response.getTotal());
        assertEquals(2, response.getPhysicalTaskCount());
        assertEquals(2L, response.getExecutionMs());
        verify(fixture.executionEngine(), times(2)).execute(any(PhysicalExecutionPlan.class));
    }

    @Test
    void shouldKeepLocalRowWhenScopedRelationFilterHasNoMatch() {
        Fixture fixture = fixture(List.of());
        VirtualQueryRequest request = relationRequest(List.of("id", "customer.name"));

        FilterNode activeOnly = predicate("status", FilterOperator.EQ, "ACTIVE");
        VirtualRelationRequest relation = new VirtualRelationRequest();
        relation.setRelationCode("customer");
        relation.setFilter(activeOnly);
        request.setRelations(List.of(relation));

        VirtualQueryResponse response = fixture.service().query(request);

        assertEquals(1L, response.getTotal());
        assertEquals(2, response.getPhysicalTaskCount());
        assertEquals(2L, response.getExecutionMs());
        assertEquals(1L, response.getRecords().get(0).get("id"));
        assertTrue(response.getRecords().get(0).containsKey("customer.name"));
        assertNull(response.getRecords().get(0).get("customer.name"));

        VirtualQueryRequest remoteRequest = remoteCompileRequest(fixture.compiler());
        assertEquals("status", remoteRequest.getFilter().getField());
        assertEquals(FilterOperator.EQ, remoteRequest.getFilter().getOperator());
    }

    @Test
    void shouldFetchOnlyJoinKeyWhenNoRemoteFieldIsProjected() {
        Fixture fixture = fixture(List.of(Map.of(
                "id", 10L,
                "name", "Alice",
                "status", "ACTIVE"
        )));
        VirtualQueryRequest request = relationRequest(List.of("id"));
        request.setRelationCodes(List.of("customer"));

        VirtualQueryResponse response = fixture.service().query(request);

        assertEquals(List.of(Map.of("id", 1L)), response.getRecords());
        VirtualQueryRequest remoteRequest = remoteCompileRequest(fixture.compiler());
        assertEquals(List.of("id"), remoteRequest.getFields());
    }

    @Test
    void shouldRejectOneToManyExpansionForDetailQuery() {
        Fixture fixture = fixture(List.of(
                Map.of("id", 10L, "name", "Alice"),
                Map.of("id", 10L, "name", "Alicia")
        ));
        VirtualQueryRequest request = relationRequest(List.of("id", "customer.name"));
        request.setRelationCodes(List.of("customer"));

        VirtualDataException exception = assertThrows(
                VirtualDataException.class,
                () -> fixture.service().query(request)
        );

        assertEquals("RELATION_CARDINALITY_UNSUPPORTED", exception.getCode());
    }

    @Test
    void shouldAttachCollectionRelationWithoutDuplicatingRootRecord() {
        Fixture fixture = fixture(List.of(
                Map.of("id", 10L, "name", "Alice"),
                Map.of("id", 10L, "name", "Alicia")
        ), RelationResultMode.COLLECTION);
        VirtualQueryRequest request = relationRequest(List.of("id", "customer.name"));
        request.setRelationCodes(List.of("customer"));
        request.setExactTotal(true);

        VirtualQueryResponse response = fixture.service().query(request);

        assertEquals(1L, response.getTotal());
        assertEquals(1, response.getRecords().size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customers = (List<Map<String, Object>>) response.getRecords().get(0).get("customer");
        assertEquals(List.of("Alice", "Alicia"), customers.stream().map(item -> item.get("name")).toList());
    }

    @Test
    void shouldRejectCollectionRelationAsGlobalFilterField() {
        Fixture fixture = fixture(List.of(), RelationResultMode.COLLECTION);
        VirtualQueryRequest request = relationRequest(List.of("id", "customer.name"));
        request.setRelationCodes(List.of("customer"));
        request.setFilter(predicate("customer.name", FilterOperator.EQ, "Alice"));

        VirtualDataException exception = assertThrows(
                VirtualDataException.class,
                () -> fixture.service().query(request)
        );

        assertEquals("RELATION_COLLECTION_OPERATION_UNSUPPORTED", exception.getCode());
    }

    private VirtualQueryRequest remoteCompileRequest(VirtualLogicalPlanCompiler compiler) {
        ArgumentCaptor<VirtualQueryRequest> captor = ArgumentCaptor.forClass(VirtualQueryRequest.class);
        verify(compiler, times(2)).compile(any(CatalogSnapshot.class), captor.capture(), anySet());
        return captor.getAllValues().stream()
                .filter(request -> "customer".equals(request.getEntityCode()))
                .findFirst()
                .orElseThrow();
    }

    private VirtualQueryRequest relationRequest(List<String> fields) {
        VirtualQueryRequest request = new VirtualQueryRequest();
        request.setEntityCode("order");
        request.setCatalogVersion(1L);
        request.setFields(fields);
        return request;
    }

    private FilterNode predicate(String field, FilterOperator operator, Object value) {
        FilterNode node = new FilterNode();
        node.setType(FilterType.PREDICATE);
        node.setField(field);
        node.setOperator(operator);
        node.setValue(value);
        return node;
    }

    private Fixture fixture(List<Map<String, Object>> remoteRows) {
        return fixture(remoteRows, RelationResultMode.OBJECT);
    }

    private Fixture fixture(List<Map<String, Object>> remoteRows, RelationResultMode relationResultMode) {
        CatalogSnapshot local = localSnapshot(relationResultMode);
        CatalogSnapshot remote = remoteSnapshot();
        VirtualCatalogService catalogService = mock(VirtualCatalogService.class);
        VirtualLogicalPlanCompiler compiler = mock(VirtualLogicalPlanCompiler.class);
        PhysicalPlanGenerator planGenerator = mock(PhysicalPlanGenerator.class);
        PhysicalExecutionEngine executionEngine = mock(PhysicalExecutionEngine.class);

        when(catalogService.requirePublished(eq("order"), eq(1L))).thenReturn(local);
        when(catalogService.requirePublished(eq(2L))).thenReturn(remote);
        when(compiler.compile(any(CatalogSnapshot.class), any(VirtualQueryRequest.class), anySet()))
                .thenAnswer(invocation -> logicalPlan(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2)
                ));
        when(planGenerator.generate(any(CatalogSnapshot.class), any(VirtualLogicalPlan.class)))
                .thenAnswer(invocation -> {
                    CatalogSnapshot snapshot = invocation.getArgument(0);
                    VirtualLogicalPlan logicalPlan = invocation.getArgument(1);
                    return new PhysicalExecutionPlan(
                            "plan-" + snapshot.entityCode(), snapshot, logicalPlan, List.of(), List.of()
                    );
                });
        when(executionEngine.execute(any(PhysicalExecutionPlan.class))).thenAnswer(invocation -> {
            PhysicalExecutionPlan plan = invocation.getArgument(0);
            List<Map<String, Object>> rows = "order".equals(plan.snapshot().entityCode())
                    ? List.of(Map.of("id", 1L, "customerId", 10L))
                    : remoteRows;
            return new PhysicalExecutionEngine.ExecutionRows(rows, rows.size(), 1, 1);
        });

        FilterEvaluator filterEvaluator = new FilterEvaluator();
        VirtualDataQueryService service = new VirtualDataQueryService(
                catalogService,
                compiler,
                planGenerator,
                executionEngine,
                new VirtualResultFinalizer(filterEvaluator),
                filterEvaluator
        );
        return new Fixture(service, compiler, executionEngine);
    }

    private VirtualLogicalPlan logicalPlan(
            CatalogSnapshot snapshot,
            VirtualQueryRequest request,
            Set<String> additionalRequiredFields
    ) {
        List<String> projections = request.getFields().stream().filter(field -> !field.contains(".")).toList();
        Set<String> required = new LinkedHashSet<>(projections);
        required.addAll(additionalRequiredFields);
        VirtualPage page = request.getPage() == null ? new VirtualPage() : request.getPage();
        return new VirtualLogicalPlan(
                snapshot.entityCode(), snapshot.catalogVersion(), request.getQueryType(), projections,
                Set.copyOf(required), request.getFilter(), new ArrayList<>(request.getRelationCodes()),
                request.getAggregates(), request.getGroupBy(), request.getSorts(), page,
                request.getConsistency() == null ? ConsistencyLevel.STRONG : request.getConsistency(),
                16, 10000, 30000, true
        );
    }

    private CatalogSnapshot localSnapshot() {
        return localSnapshot(RelationResultMode.OBJECT);
    }

    private CatalogSnapshot localSnapshot(RelationResultMode relationResultMode) {
        CatalogSnapshot.VirtualField id = new CatalogSnapshot.VirtualField(
                1L, "id", "ID", LogicalType.LONG, false, true, 0, true);
        CatalogSnapshot.VirtualField customerId = new CatalogSnapshot.VirtualField(
                2L, "customerId", "Customer ID", LogicalType.LONG, false, false, 1, true);
        CatalogSnapshot.Relation customer = new CatalogSnapshot.Relation(
                100L, "customer", "Customer", 1L, 2L, 2L, 20L, relationResultMode, true);
        return new CatalogSnapshot(
                1L, "order", "Order", CatalogStatus.PUBLISHED, 1, true,
                Map.of("id", id, "customerId", customerId), Map.of(1L, id, 2L, customerId),
                List.of(), Map.of(), List.of(customer)
        );
    }

    private CatalogSnapshot remoteSnapshot() {
        CatalogSnapshot.VirtualField id = new CatalogSnapshot.VirtualField(
                20L, "id", "ID", LogicalType.LONG, false, true, 0, true);
        CatalogSnapshot.VirtualField name = new CatalogSnapshot.VirtualField(
                21L, "name", "Name", LogicalType.STRING, true, false, 1, true);
        CatalogSnapshot.VirtualField status = new CatalogSnapshot.VirtualField(
                22L, "status", "Status", LogicalType.STRING, false, false, 2, true);
        return new CatalogSnapshot(
                2L, "customer", "Customer", CatalogStatus.PUBLISHED, 1, true,
                Map.of("id", id, "name", name, "status", status),
                Map.of(20L, id, 21L, name, 22L, status), List.of(), Map.of(), List.of()
        );
    }

    private record Fixture(
            VirtualDataQueryService service,
            VirtualLogicalPlanCompiler compiler,
            PhysicalExecutionEngine executionEngine
    ) {
    }
}
