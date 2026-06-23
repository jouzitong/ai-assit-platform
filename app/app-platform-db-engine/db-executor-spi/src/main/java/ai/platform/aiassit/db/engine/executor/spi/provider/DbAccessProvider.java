package ai.platform.aiassit.db.engine.executor.spi.provider;

import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessSourceType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;

public interface DbAccessProvider {

    boolean supports(DbAccessSourceType sourceType, DbAccessDbType dbType);

    DbAccessExecutor createExecutor(DbAccessContext context) throws DbAccessException;
}
