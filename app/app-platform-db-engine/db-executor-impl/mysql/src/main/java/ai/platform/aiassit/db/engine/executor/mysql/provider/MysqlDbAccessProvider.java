package ai.platform.aiassit.db.engine.executor.mysql.provider;

import ai.platform.aiassit.db.engine.executor.mysql.support.MysqlConnectionSupport;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessSourceType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.provider.DbAccessExecutor;
import ai.platform.aiassit.db.engine.executor.spi.provider.DbAccessProvider;
import org.springframework.stereotype.Component;

@Component
public class MysqlDbAccessProvider implements DbAccessProvider {

    private final MysqlConnectionSupport connectionSupport;

    public MysqlDbAccessProvider(MysqlConnectionSupport connectionSupport) {
        this.connectionSupport = connectionSupport;
    }

    @Override
    public boolean supports(DbAccessSourceType sourceType, DbAccessDbType dbType) {
        return sourceType == DbAccessSourceType.DATABASE && dbType == DbAccessDbType.MYSQL;
    }

    @Override
    public DbAccessExecutor createExecutor(DbAccessContext context) throws DbAccessException {
        return new MysqlDbAccessExecutor(connectionSupport, context);
    }
}
