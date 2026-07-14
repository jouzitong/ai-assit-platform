package ai.platform.aiassit.data.virtualization.spi.catalog;

/** Physical field metadata needed to draft and validate virtual mappings. */
public record PhysicalFieldDefinition(
        long id,
        String sourceKey,
        String tableName,
        String columnName,
        String columnComment,
        String dataType,
        boolean nullable,
        boolean primaryKey,
        String defaultValue,
        Integer ordinalPosition,
        boolean enabled
) {
}
