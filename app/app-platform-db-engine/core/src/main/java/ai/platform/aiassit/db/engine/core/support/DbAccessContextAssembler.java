package ai.platform.aiassit.db.engine.core.support;

import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessSourceType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessAuth;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessDatabase;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessNetwork;
import ai.platform.aiassit.db.engine.meta.entity.config.DataSourceConfig;
import ai.platform.aiassit.db.engine.meta.entity.config.DatabaseConnectionConfig;
import ai.platform.aiassit.db.engine.meta.entity.config.DatabaseSourceConfig;
import ai.platform.aiassit.db.engine.meta.entity.config.HttpApiSourceConfig;
import ai.platform.aiassit.db.engine.meta.entity.config.DbDataSourceAuthConfig;
import ai.platform.aiassit.db.engine.meta.entity.config.DbDataSourceNetworkConfig;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbDataSourceDTO;
import ai.platform.aiassit.db.engine.meta.enums.DbDataSourceDbType;
import ai.platform.aiassit.db.engine.meta.enums.DbDataSourceType;
import org.springframework.stereotype.Component;

@Component
public class DbAccessContextAssembler {

    public DbAccessContext toContext(DbDataSourceDTO dataSource) throws DbAccessException {
        if (dataSource == null) {
            throw new DbAccessException("数据源不存在");
        }
        DataSourceConfig config = dataSource.getConfig();
        if (config == null) {
            throw new DbAccessException("数据源配置不能为空");
        }
        DbAccessSourceType sourceType = toSourceType(dataSource.getSourceType());
        if (sourceType == DbAccessSourceType.DATABASE && !(config instanceof DatabaseSourceConfig)) {
            throw new DbAccessException("DATABASE 数据源必须使用 DatabaseSourceConfig");
        }
        if (sourceType == DbAccessSourceType.HTTP_API && !(config instanceof HttpApiSourceConfig)) {
            throw new DbAccessException("HTTP_API 数据源必须使用 HttpApiSourceConfig");
        }
        DatabaseSourceConfig databaseConfig = config instanceof DatabaseSourceConfig value ? value : null;
        HttpApiSourceConfig httpConfig = config instanceof HttpApiSourceConfig value ? value : null;
        DbAccessDbType dbType = databaseConfig == null ? null : toDbType(databaseConfig.getDbType());
        return DbAccessContext.builder()
                .sourceKey(dataSource.getSourceKey())
                .sourceName(dataSource.getSourceName())
                .sourceType(sourceType)
                .dbType(dbType)
                .endpoint(httpConfig == null ? null : httpConfig.getBaseUrl())
                .network(toNetwork(databaseConfig == null ? httpConfig == null ? null : httpConfig.getNetwork() : databaseConfig.getNetwork()))
                .auth(toAuth(databaseConfig == null ? httpConfig == null ? null : httpConfig.getCredential() : databaseConfig.getCredential()))
                .database(databaseConfig == null ? null : toDatabase(databaseConfig.getConnection()))
                .attributes(databaseConfig == null ? httpConfig == null ? null : httpConfig.getAttributes() : databaseConfig.getDriverProperties())
                .build();
    }

    private DbAccessSourceType toSourceType(DbDataSourceType sourceType) throws DbAccessException {
        if (sourceType == null) {
            throw new DbAccessException("数据源类型不能为空");
        }
        return switch (sourceType) {
            case DATABASE -> DbAccessSourceType.DATABASE;
            case HTTP_API -> DbAccessSourceType.HTTP_API;
            case SERVICE_API -> DbAccessSourceType.SERVICE_API;
            case FILE -> DbAccessSourceType.FILE;
            case STREAM -> DbAccessSourceType.STREAM;
        };
    }

    private DbAccessDbType toDbType(DbDataSourceDbType dbType) throws DbAccessException {
        if (dbType == null) {
            throw new DbAccessException("数据库类型不能为空");
        }
        if (dbType != DbDataSourceDbType.MYSQL) {
            throw new DbAccessException("暂不支持的数据库类型: " + dbType);
        }
        return DbAccessDbType.MYSQL;
    }

    private DbAccessNetwork toNetwork(DbDataSourceNetworkConfig network) {
        if (network == null) {
            return null;
        }
        return DbAccessNetwork.builder()
                .connectTimeoutMs(network.getConnectTimeoutMs())
                .readTimeoutMs(network.getReadTimeoutMs())
                .writeTimeoutMs(network.getWriteTimeoutMs())
                .build();
    }

    private DbAccessAuth toAuth(DbDataSourceAuthConfig auth) {
        if (auth == null) {
            return null;
        }
        return DbAccessAuth.builder()
                .username(auth.getUsername())
                .password(auth.getPasswordCiphertext())
                .credentialRef(auth.getCredentialRef())
                .build();
    }

    private DbAccessDatabase toDatabase(DatabaseConnectionConfig connection) throws DbAccessException {
        if (connection == null || connection.getMode() == null) {
            throw new DbAccessException("数据库连接配置不能为空");
        }
        if (connection.getMode() == ai.platform.aiassit.db.engine.meta.enums.DatabaseConnectionMode.JDBC_URL
                && !org.springframework.util.StringUtils.hasText(connection.getJdbcUrl())) {
            throw new DbAccessException("JDBC_URL 模式必须配置 jdbcUrl");
        }
        if (connection.getMode() == ai.platform.aiassit.db.engine.meta.enums.DatabaseConnectionMode.HOST_PORT
                && (!org.springframework.util.StringUtils.hasText(connection.getHost())
                || !org.springframework.util.StringUtils.hasText(connection.getDatabaseName()))) {
            throw new DbAccessException("HOST_PORT 模式必须配置 host 和 databaseName");
        }
        if (connection.getMode() == ai.platform.aiassit.db.engine.meta.enums.DatabaseConnectionMode.JDBC_URL) {
            return DbAccessDatabase.builder().jdbcUrl(connection.getJdbcUrl()).schemaName(connection.getSchemaName()).build();
        }
        return DbAccessDatabase.builder()
                .host(connection.getHost())
                .port(connection.getPort())
                .databaseName(connection.getDatabaseName())
                .schemaName(connection.getSchemaName())
                .build();
    }
}
