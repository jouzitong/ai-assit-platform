package ai.platform.aiassit.data.virtualization.spi.catalog;

import java.util.List;
import java.util.Optional;

/** Provides physical metadata without exposing DB Engine persistence types. */
public interface PhysicalCatalogPort {

    Optional<PhysicalTableDefinition> findTable(long tableMetaId);

    List<PhysicalFieldDefinition> fields(long tableMetaId);

    Optional<PhysicalFieldDefinition> findField(long fieldMetaId);
}
