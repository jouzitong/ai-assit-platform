package ai.platform.aiassit.data.virtualization.spi.catalog;

/** Stable physical table identity used by virtual catalog validation. */
public record PhysicalTableDefinition(
        long id,
        String sourceKey,
        String tableName,
        boolean enabled
) {
}
