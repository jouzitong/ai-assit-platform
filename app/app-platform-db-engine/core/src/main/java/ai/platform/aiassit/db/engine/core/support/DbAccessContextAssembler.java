package ai.platform.aiassit.db.engine.core.support;

import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessSourceType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessAuth;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessDatabase;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessNetwork;
import ai.platform.aiassit.db.engine.meta.entity.config.DbDataSourceAuthConfig;
import ai.platform.aiassit.db.engine.meta.entity.config.DbDataSourceConfig;
import ai.platform.aiassit.db.engine.meta.entity.config.DbDataSourceDatabaseConfig;
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
        DbDataSourceConfig config = dataSource.getConfig();
        if (config == null) {
            throw new DbAccessException("数据源配置不能为空");
        }
        DbAccessSourceType sourceType = toSourceType(dataSource.getSourceType());
        DbAccessDbType dbType = toDbType(config.getDbType());
        return DbAccessContext.builder()
                .sourceKey(dataSource.getSourceKey())
                .sourceName(dataSource.getSourceName())
                .sourceType(sourceType)
                .dbType(dbType)
                .endpoint(config.getEndpoint())
                .network(toNetwork(config.getNetwork()))
                .auth(toAuth(config.getAuth()))
                .database(toDatabase(config.getDatabase()))
                .attributes(config.getAttributes())
                .build();
    }

    private DbAccessSourceType toSourceType(DbDataSourceType sourceType) throws DbAccessException {
        if (sourceType == null) {
            throw new DbAccessException("数据源类型不能为空");
        }
        if (sourceType != DbDataSourceType.DATABASE) {
            throw new DbAccessException("暂不支持的数据源类型: " + sourceType);
        }
        return DbAccessSourceType.DATABASE;
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

    private DbAccessDatabase toDatabase(DbDataSourceDatabaseConfig database) {
        if (database == null) {
            return null;
        }
        return DbAccessDatabase.builder()
                .host(database.getHost())
                .port(database.getPort())
                .databaseName(database.getDatabaseName())
                .schemaName(database.getSchemaName())
                .jdbcUrl(database.getJdbcUrl())
                .build();
    }
}
