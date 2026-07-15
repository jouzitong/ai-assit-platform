package ai.platform.aiassit.data.virtualization.core.catalog;

import ai.platform.aiassit.data.virtualization.api.dto.VirtualCatalogDescriptor;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.LogicalType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.RelationResultMode;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualEntityEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualFieldEntity;
import ai.platform.aiassit.data.virtualization.data.service.VirtualCatalogDataRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VirtualCatalogServiceTest {

    @Test
    void shouldExposeOnlyVirtualRelationMigrationMetadata() {
        CatalogAssembler assembler = mock(CatalogAssembler.class);
        VirtualCatalogDataRepository repository = mock(VirtualCatalogDataRepository.class);
        VirtualEntityEntity orders = entity(1L, "orders");
        VirtualEntityEntity customers = entity(2L, "customers");
        VirtualFieldEntity customerId = field(10L, "customer_id");
        VirtualFieldEntity customerPk = field(20L, "id");
        CatalogSnapshot.VirtualField id = new CatalogSnapshot.VirtualField(
                1L, "id", "ID", LogicalType.LONG, false, true, 0, true);
        CatalogSnapshot.Relation relation = new CatalogSnapshot.Relation(
                100L, "customer", "Customer", 1L, 10L, 2L, 20L, true);
        CatalogSnapshot snapshot = new CatalogSnapshot(
                1L, "orders", "Orders", CatalogStatus.PUBLISHED, 7L, true,
                Map.of("id", id), Map.of(1L, id), List.of(), Map.of(), List.of(relation));

        when(repository.entityByCode("orders")).thenReturn(orders);
        when(repository.entityById(2L)).thenReturn(customers);
        when(repository.fieldById(10L)).thenReturn(customerId);
        when(repository.fieldById(20L)).thenReturn(customerPk);
        when(assembler.byEntityCode("orders")).thenReturn(snapshot);

        VirtualCatalogDescriptor descriptor = new VirtualCatalogService(assembler, repository)
                .describePublished("orders", 7L);

        assertEquals(List.of("customer"), descriptor.relationCodes());
        assertEquals("customers", descriptor.relations().get(0).targetEntityCode());
        assertEquals(Map.of("customer_id", "id"), descriptor.relations().get(0).localToRemoteFields());
        assertEquals(RelationResultMode.OBJECT, descriptor.relations().get(0).resultMode());
    }

    @Test
    void shouldExposeCollectionResultModeFromVirtualRelationDefinition() {
        CatalogAssembler assembler = mock(CatalogAssembler.class);
        VirtualCatalogDataRepository repository = mock(VirtualCatalogDataRepository.class);
        VirtualEntityEntity orders = entity(1L, "orders");
        VirtualEntityEntity items = entity(2L, "items");
        VirtualFieldEntity orderId = field(10L, "id");
        VirtualFieldEntity itemOrderId = field(20L, "order_id");
        CatalogSnapshot.VirtualField id = new CatalogSnapshot.VirtualField(
                1L, "id", "ID", LogicalType.LONG, false, true, 0, true);
        CatalogSnapshot.Relation relation = new CatalogSnapshot.Relation(
                100L, "items", "Items", 1L, 10L, 2L, 20L, RelationResultMode.COLLECTION, true);
        CatalogSnapshot snapshot = new CatalogSnapshot(
                1L, "orders", "Orders", CatalogStatus.PUBLISHED, 7L, true,
                Map.of("id", id), Map.of(1L, id), List.of(), Map.of(), List.of(relation));

        when(repository.entityByCode("orders")).thenReturn(orders);
        when(repository.entityById(2L)).thenReturn(items);
        when(repository.fieldById(10L)).thenReturn(orderId);
        when(repository.fieldById(20L)).thenReturn(itemOrderId);
        when(assembler.byEntityCode("orders")).thenReturn(snapshot);

        VirtualCatalogDescriptor descriptor = new VirtualCatalogService(assembler, repository)
                .describePublished("orders", 7L);

        assertEquals(RelationResultMode.COLLECTION, descriptor.relations().get(0).resultMode());
    }

    @Test
    void shouldExposeReverseRelationWithExplicitCollectionModeAndFlippedFields() {
        CatalogAssembler assembler = mock(CatalogAssembler.class);
        VirtualCatalogDataRepository repository = mock(VirtualCatalogDataRepository.class);
        VirtualEntityEntity orders = entity(1L, "orders");
        VirtualEntityEntity items = entity(2L, "items");
        VirtualFieldEntity orderId = field(10L, "id", 1L, true);
        VirtualFieldEntity itemOrderId = field(20L, "order_id", 2L, false);
        CatalogSnapshot.VirtualField localOrderId = new CatalogSnapshot.VirtualField(
                20L, "order_id", "Order ID", LogicalType.LONG, false, false, 0, true);
        CatalogSnapshot.Relation relation = new CatalogSnapshot.Relation(
                100L, "items", "Items", 1L, 10L, 2L, 20L,
                RelationResultMode.COLLECTION, RelationResultMode.COLLECTION, true);
        CatalogSnapshot snapshot = new CatalogSnapshot(
                2L, "items", "Items", CatalogStatus.PUBLISHED, 7L, true,
                Map.of("order_id", localOrderId), Map.of(20L, localOrderId),
                List.of(), Map.of(), List.of(relation));

        when(repository.entityByCode("items")).thenReturn(items);
        when(repository.entityById(1L)).thenReturn(orders);
        when(repository.fieldById(10L)).thenReturn(orderId);
        when(repository.fieldById(20L)).thenReturn(itemOrderId);
        when(assembler.byEntityCode("items")).thenReturn(snapshot);

        VirtualCatalogDescriptor descriptor = new VirtualCatalogService(assembler, repository)
                .describePublished("items", 7L);

        assertEquals(List.of(relation), snapshot.relationGroup("items"));
        assertEquals(List.of(relation), snapshot.relationGroup("items", 1L));
        assertEquals(List.of("items"), descriptor.relationCodes());
        assertEquals("orders", descriptor.relations().get(0).targetEntityCode());
        assertEquals(Map.of("order_id", "id"), descriptor.relations().get(0).localToRemoteFields());
        assertEquals(RelationResultMode.COLLECTION, descriptor.relations().get(0).resultMode());
    }

    @Test
    void shouldInferReverseObjectWhenSourceMappingsCoverSourcePrimaryKey() {
        CatalogAssembler assembler = mock(CatalogAssembler.class);
        VirtualCatalogDataRepository repository = mock(VirtualCatalogDataRepository.class);
        VirtualEntityEntity profiles = entity(1L, "profiles");
        VirtualEntityEntity employees = entity(2L, "employees");
        VirtualFieldEntity profileEmployeeId = field(10L, "employee_id", 1L, true);
        VirtualFieldEntity employeeId = field(20L, "id", 2L, true);
        CatalogSnapshot.VirtualField localEmployeeId = new CatalogSnapshot.VirtualField(
                20L, "id", "ID", LogicalType.LONG, false, true, 0, true);
        CatalogSnapshot.Relation relation = new CatalogSnapshot.Relation(
                100L, "employee", "Employee", 1L, 10L, 2L, 20L,
                RelationResultMode.OBJECT, null, true);
        CatalogSnapshot snapshot = new CatalogSnapshot(
                2L, "employees", "Employees", CatalogStatus.PUBLISHED, 7L, true,
                Map.of("id", localEmployeeId), Map.of(20L, localEmployeeId),
                List.of(), Map.of(), List.of(relation));

        when(repository.entityByCode("employees")).thenReturn(employees);
        when(repository.entityById(1L)).thenReturn(profiles);
        when(repository.fields(1L)).thenReturn(List.of(profileEmployeeId));
        when(repository.fieldById(10L)).thenReturn(profileEmployeeId);
        when(repository.fieldById(20L)).thenReturn(employeeId);
        when(assembler.byEntityCode("employees")).thenReturn(snapshot);

        VirtualCatalogDescriptor descriptor = new VirtualCatalogService(assembler, repository)
                .describePublished("employees", 7L);

        assertEquals(RelationResultMode.OBJECT, descriptor.relations().get(0).resultMode());
    }

    @Test
    void shouldInferReverseCollectionWhenSourceMappingsDoNotCoverSourcePrimaryKey() {
        CatalogAssembler assembler = mock(CatalogAssembler.class);
        VirtualCatalogDataRepository repository = mock(VirtualCatalogDataRepository.class);
        VirtualEntityEntity assignments = entity(1L, "assignments");
        VirtualEntityEntity employees = entity(2L, "employees");
        VirtualFieldEntity assignmentId = field(10L, "id", 1L, true);
        VirtualFieldEntity assignmentEmployeeId = field(11L, "employee_id", 1L, false);
        VirtualFieldEntity employeeId = field(20L, "id", 2L, true);
        CatalogSnapshot.VirtualField localEmployeeId = new CatalogSnapshot.VirtualField(
                20L, "id", "ID", LogicalType.LONG, false, true, 0, true);
        CatalogSnapshot.Relation relation = new CatalogSnapshot.Relation(
                100L, "employee", "Employee", 1L, 11L, 2L, 20L,
                RelationResultMode.OBJECT, null, true);
        CatalogSnapshot snapshot = new CatalogSnapshot(
                2L, "employees", "Employees", CatalogStatus.PUBLISHED, 7L, true,
                Map.of("id", localEmployeeId), Map.of(20L, localEmployeeId),
                List.of(), Map.of(), List.of(relation));

        when(repository.entityByCode("employees")).thenReturn(employees);
        when(repository.entityById(1L)).thenReturn(assignments);
        when(repository.fields(1L)).thenReturn(List.of(assignmentId, assignmentEmployeeId));
        when(repository.fieldById(11L)).thenReturn(assignmentEmployeeId);
        when(repository.fieldById(20L)).thenReturn(employeeId);
        when(assembler.byEntityCode("employees")).thenReturn(snapshot);

        VirtualCatalogDescriptor descriptor = new VirtualCatalogService(assembler, repository)
                .describePublished("employees", 7L);

        assertEquals(RelationResultMode.COLLECTION, descriptor.relations().get(0).resultMode());
    }

    @Test
    void shouldKeepInboundRelationsWithSameCodeSeparatedByEndpoints() {
        CatalogAssembler assembler = mock(CatalogAssembler.class);
        VirtualCatalogDataRepository repository = mock(VirtualCatalogDataRepository.class);
        VirtualEntityEntity sourceA = entity(1L, "source_a");
        VirtualEntityEntity sourceB = entity(2L, "source_b");
        VirtualEntityEntity target = entity(3L, "target");
        VirtualFieldEntity sourceAField = field(10L, "target_id", 1L, false);
        VirtualFieldEntity sourceBField = field(20L, "target_id", 2L, false);
        VirtualFieldEntity targetAField = field(30L, "id_a", 3L, false);
        VirtualFieldEntity targetBField = field(31L, "id_b", 3L, false);
        CatalogSnapshot.VirtualField idA = new CatalogSnapshot.VirtualField(
                30L, "id_a", "ID A", LogicalType.LONG, false, false, 0, true);
        CatalogSnapshot.VirtualField idB = new CatalogSnapshot.VirtualField(
                31L, "id_b", "ID B", LogicalType.LONG, false, false, 1, true);
        CatalogSnapshot.Relation relationA = new CatalogSnapshot.Relation(
                100L, "owner", "Owner A", 1L, 10L, 3L, 30L,
                RelationResultMode.OBJECT, RelationResultMode.OBJECT, true);
        CatalogSnapshot.Relation relationB = new CatalogSnapshot.Relation(
                101L, "owner", "Owner B", 2L, 20L, 3L, 31L,
                RelationResultMode.OBJECT, RelationResultMode.COLLECTION, true);
        CatalogSnapshot snapshot = new CatalogSnapshot(
                3L, "target", "Target", CatalogStatus.PUBLISHED, 7L, true,
                Map.of("id_a", idA, "id_b", idB), Map.of(30L, idA, 31L, idB),
                List.of(), Map.of(), List.of(relationA, relationB));

        when(repository.entityByCode("target")).thenReturn(target);
        when(repository.entityById(1L)).thenReturn(sourceA);
        when(repository.entityById(2L)).thenReturn(sourceB);
        when(repository.fieldById(10L)).thenReturn(sourceAField);
        when(repository.fieldById(20L)).thenReturn(sourceBField);
        when(repository.fieldById(30L)).thenReturn(targetAField);
        when(repository.fieldById(31L)).thenReturn(targetBField);
        when(assembler.byEntityCode("target")).thenReturn(snapshot);

        VirtualCatalogDescriptor descriptor = new VirtualCatalogService(assembler, repository)
                .describePublished("target", 7L);

        assertEquals(List.of("owner"), descriptor.relationCodes());
        assertEquals(List.of("source_a", "source_b"), descriptor.relations().stream()
                .map(VirtualCatalogDescriptor.Relation::targetEntityCode).toList());
        assertEquals(List.of(RelationResultMode.OBJECT, RelationResultMode.COLLECTION),
                descriptor.relations().stream().map(VirtualCatalogDescriptor.Relation::resultMode).toList());
    }

    private VirtualEntityEntity entity(Long id, String code) {
        VirtualEntityEntity entity = new VirtualEntityEntity();
        entity.setId(id);
        entity.setEntityCode(code);
        entity.setEntityName(code);
        entity.setCatalogVersion(7L);
        entity.setStatus(CatalogStatus.PUBLISHED);
        entity.setEnabled(true);
        return entity;
    }

    private VirtualFieldEntity field(Long id, String code) {
        return field(id, code, null, false);
    }

    private VirtualFieldEntity field(Long id, String code, Long entityId, boolean primaryKey) {
        VirtualFieldEntity field = new VirtualFieldEntity();
        field.setId(id);
        field.setEntityId(entityId);
        field.setFieldCode(code);
        field.setFieldName(code);
        field.setLogicalType(LogicalType.LONG);
        field.setPrimaryKey(primaryKey);
        field.setEnabled(true);
        return field;
    }
}
