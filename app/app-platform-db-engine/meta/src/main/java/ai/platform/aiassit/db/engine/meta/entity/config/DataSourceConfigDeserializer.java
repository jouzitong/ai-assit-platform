package ai.platform.aiassit.db.engine.meta.entity.config;

import ai.platform.aiassit.db.engine.meta.enums.DataSourceConfigType;
import ai.platform.aiassit.db.engine.meta.enums.DatabaseConnectionMode;
import ai.platform.aiassit.db.engine.meta.enums.DbDataSourceDbType;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.Map;

/**
 * 兼容旧 config JSON：旧结构默认按数据库配置解析，并归一为 v2 的 DatabaseSourceConfig。
 */
public class DataSourceConfigDeserializer extends StdDeserializer<DataSourceConfig> {

    public DataSourceConfigDeserializer() {
        super(DataSourceConfig.class);
    }

    @Override
    public DataSourceConfig deserialize(JsonParser parser, DeserializationContext context) throws IOException, JsonProcessingException {
        ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        JsonNode root = mapper.readTree(parser);
        String configType = root.path("configType").asText(null);
        if (DataSourceConfigType.HTTP_API.name().equals(configType)) {
            return mapper.treeToValue(root, HttpApiSourceConfig.class);
        }
        if (DataSourceConfigType.DATABASE.name().equals(configType)) {
            return mapper.treeToValue(root, DatabaseSourceConfig.class);
        }
        return legacyDatabaseConfig(root, mapper);
    }

    private DatabaseSourceConfig legacyDatabaseConfig(JsonNode root, ObjectMapper mapper) {
        JsonNode legacyDatabase = root.path("database");
        DbDataSourceDatabaseConfig database = legacyDatabase.isMissingNode() || legacyDatabase.isNull()
                ? null : mapper.convertValue(legacyDatabase, DbDataSourceDatabaseConfig.class);
        String jdbcUrl = database == null ? null : database.getJdbcUrl();
        String endpoint = root.path("endpoint").asText(null);
        if (jdbcUrl == null && endpoint != null && endpoint.startsWith("jdbc:")) {
            jdbcUrl = endpoint;
        }
        DatabaseConnectionConfig connection = DatabaseConnectionConfig.builder()
                .mode(jdbcUrl == null ? DatabaseConnectionMode.HOST_PORT : DatabaseConnectionMode.JDBC_URL)
                .jdbcUrl(jdbcUrl)
                .host(database == null ? null : database.getHost())
                .port(database == null ? null : database.getPort())
                .databaseName(database == null ? null : database.getDatabaseName())
                .schemaName(database == null ? null : database.getSchemaName())
                .build();
        DbDataSourceDbType dbType = root.hasNonNull("dbType")
                ? mapper.convertValue(root.get("dbType"), DbDataSourceDbType.class)
                : database == null ? null : database.getDbType();
        return DatabaseSourceConfig.builder()
                .dbType(dbType)
                .connection(connection)
                .credential(root.has("auth") ? mapper.convertValue(root.get("auth"), DbDataSourceAuthConfig.class) : null)
                .network(root.has("network") ? mapper.convertValue(root.get("network"), DbDataSourceNetworkConfig.class) : null)
                .driverProperties(root.has("attributes") ? mapper.convertValue(root.get("attributes"), Map.class) : Map.of())
                .build();
    }
}
