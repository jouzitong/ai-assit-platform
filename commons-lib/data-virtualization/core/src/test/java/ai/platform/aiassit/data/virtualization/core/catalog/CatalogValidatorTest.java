package ai.platform.aiassit.data.virtualization.core.catalog;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.BindingRole;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FieldSide;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.LogicalType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.TransformMode;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformerRegistry;
import ai.platform.aiassit.data.virtualization.data.service.VirtualCatalogDataRepository;
import ai.platform.aiassit.data.virtualization.spi.catalog.PhysicalCatalogPort;
import ai.platform.aiassit.data.virtualization.spi.catalog.PhysicalFieldDefinition;
import ai.platform.aiassit.data.virtualization.spi.catalog.PhysicalTableDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogValidatorTest {

    @Test
    void shouldAcceptDisabledRuleWhenDefaultReadMappingMatchesPhysicalField() {
        PhysicalCatalogPort physicalCatalogPort = mock(PhysicalCatalogPort.class);
        when(physicalCatalogPort.findTable(50L)).thenReturn(Optional.of(new PhysicalTableDefinition(
                50L, "source", "orders", true
        )));
        when(physicalCatalogPort.fields(50L)).thenReturn(List.of(new PhysicalFieldDefinition(
                20L, "source", "orders", "order_id", "", "BIGINT", false, true, null, 1, true
        )));
        DefaultFieldMappingResolver resolver = new DefaultFieldMappingResolver(physicalCatalogPort);
        CatalogValidator validator = new CatalogValidator(
                mock(FieldTransformerRegistry.class), physicalCatalogPort,
                mock(VirtualCatalogDataRepository.class), resolver
        );
        CatalogSnapshot.VirtualField orderId = new CatalogSnapshot.VirtualField(
                10L, "orderId", "Order ID", LogicalType.LONG, false, true, 0, true
        );
        CatalogSnapshot.Port physicalPort = new CatalogSnapshot.Port(
                100L, FieldSide.PHYSICAL, "physical", null, 20L, "order_id", 0, false
        );
        CatalogSnapshot.Port virtualPort = new CatalogSnapshot.Port(
                101L, FieldSide.VIRTUAL, "virtual", 10L, null, null, 0, false
        );
        CatalogSnapshot.TransformRule disabledRule = new CatalogSnapshot.TransformRule(
                30L, 40L, "identity_orderId", "Order ID identity", TransformMode.BIDIRECTIONAL,
                "identity", 1, "identity", 1, Map.of(), Map.of(), false,
                List.of(physicalPort), List.of(virtualPort)
        );
        CatalogSnapshot.Binding binding = new CatalogSnapshot.Binding(
                40L, "primary", "default", BindingRole.PRIMARY, 50L,
                "source", "orders", true, true, 100, 0, null, true
        );
        CatalogSnapshot snapshot = new CatalogSnapshot(
                1L, "order", "Order", CatalogStatus.DRAFT, 0, true,
                Map.of("orderId", orderId), Map.of(10L, orderId), List.of(binding),
                Map.of(40L, List.of(disabledRule)), List.of()
        );

        assertDoesNotThrow(() -> validator.validate(snapshot));
    }
}
