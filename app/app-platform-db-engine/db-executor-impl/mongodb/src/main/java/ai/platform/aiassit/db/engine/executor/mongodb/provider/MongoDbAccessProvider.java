package ai.platform.aiassit.db.engine.executor.mongodb.provider;

import ai.platform.aiassit.db.engine.executor.mongodb.support.MongoConnectionSupport;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessSourceType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.provider.DbAccessExecutor;
import ai.platform.aiassit.db.engine.executor.spi.provider.DbAccessProvider;
import org.springframework.stereotype.Component;

@Component
public class MongoDbAccessProvider implements DbAccessProvider {

    private final MongoConnectionSupport connectionSupport;

    public MongoDbAccessProvider(MongoConnectionSupport connectionSupport) {
        this.connectionSupport = connectionSupport;
    }

    @Override
    public boolean supports(DbAccessSourceType sourceType, DbAccessDbType dbType) {
        return sourceType == DbAccessSourceType.DATABASE && dbType == DbAccessDbType.MONGODB;
    }

    @Override
    public DbAccessExecutor createExecutor(DbAccessContext context) throws DbAccessException {
        if (context == null || context.getDbType() != DbAccessDbType.MONGODB) {
            throw new DbAccessException("MongoDB 执行器收到不支持的数据源上下文");
        }
        return new MongoDbAccessExecutor(connectionSupport, context);
    }
}
