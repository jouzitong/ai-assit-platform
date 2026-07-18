package ai.platform.aiassit.data.virtualization.core.catalog;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FieldSide;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.TransformMode;
import ai.platform.aiassit.data.virtualization.spi.catalog.PhysicalCatalogPort;
import ai.platform.aiassit.data.virtualization.spi.catalog.PhysicalFieldDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Resolves a read-only identity mapping when no enabled explicit rule serves a virtual field. */
@Component
public class DefaultFieldMappingResolver {
    private final PhysicalCatalogPort physicalCatalogPort;

    public DefaultFieldMappingResolver(PhysicalCatalogPort physicalCatalogPort) {
        this.physicalCatalogPort = physicalCatalogPort;
    }

    public CatalogSnapshot.TransformRule resolveReadableRule(
            CatalogSnapshot snapshot,
            CatalogSnapshot.Binding binding,
            CatalogSnapshot.VirtualField field
    ) {
        CatalogSnapshot.TransformRule explicit = snapshot.readableRule(binding.id(), field.id());
        return explicit == null ? defaultReadableRule(binding, field) : explicit;
    }

    private CatalogSnapshot.TransformRule defaultReadableRule(
            CatalogSnapshot.Binding binding,
            CatalogSnapshot.VirtualField field
    ) {
        if (binding.physicalTableMetaId() == null || field.code() == null || field.code().isBlank()) {
            return null;
        }
        List<PhysicalFieldDefinition> matches = physicalCatalogPort.fields(binding.physicalTableMetaId()).stream()
                .filter(PhysicalFieldDefinition::enabled)
                .filter(item -> normalized(item.columnName()).equals(normalized(field.code())))
                .toList();
        if (matches.size() != 1) {
            return null;
        }
        PhysicalFieldDefinition physical = matches.get(0);
        CatalogSnapshot.Port physicalPort = new CatalogSnapshot.Port(
                -physical.id(), FieldSide.PHYSICAL, "physical", null,
                physical.id(), physical.columnName(), 0, false
        );
        CatalogSnapshot.Port virtualPort = new CatalogSnapshot.Port(
                -field.id(), FieldSide.VIRTUAL, "virtual", field.id(),
                null, null, 0, false
        );
        return new CatalogSnapshot.TransformRule(
                -physical.id(), binding.id(), "default_read_" + field.code(), "默认字段映射 " + field.code(),
                TransformMode.READ_ONLY, "identity", 1, null, 1,
                Map.of("configVersion", 1), Map.of(), true,
                List.of(physicalPort), List.of(virtualPort)
        );
    }

    private String normalized(String value) {
        return value == null ? "" : value.replaceAll("[_\\-\\s]", "").toLowerCase(Locale.ROOT);
    }
}
