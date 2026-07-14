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
        VirtualFieldEntity field = new VirtualFieldEntity();
        field.setId(id);
        field.setFieldCode(code);
        field.setFieldName(code);
        field.setLogicalType(LogicalType.LONG);
        field.setEnabled(true);
        return field;
    }
}
