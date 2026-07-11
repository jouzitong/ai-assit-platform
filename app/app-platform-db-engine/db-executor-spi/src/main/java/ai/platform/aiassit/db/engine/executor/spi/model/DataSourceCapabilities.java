package ai.platform.aiassit.db.engine.executor.spi.model;

/** 数据源适配器能力声明，调用方必须按能力选择操作。 */
public record DataSourceCapabilities(
        boolean read,
        boolean mutation,
        boolean schemaMetadata,
        boolean dataDefinition
) {

    public static DataSourceCapabilities readOnly() {
        return new DataSourceCapabilities(true, false, false, false);
    }
}
