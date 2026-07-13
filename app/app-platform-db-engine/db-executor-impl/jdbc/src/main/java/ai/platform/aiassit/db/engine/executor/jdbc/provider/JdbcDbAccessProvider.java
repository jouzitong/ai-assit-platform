package ai.platform.aiassit.db.engine.executor.jdbc.provider;

import ai.platform.aiassit.db.engine.executor.jdbc.support.JdbcConnectionSupport;
import ai.platform.aiassit.db.engine.executor.jdbc.support.JdbcDatabaseProfile;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessSourceType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.provider.DbAccessExecutor;
import ai.platform.aiassit.db.engine.executor.spi.provider.DbAccessProvider;
import org.springframework.stereotype.Component;

@Component
public class JdbcDbAccessProvider implements DbAccessProvider {

    private final JdbcConnectionSupport connectionSupport;

    public JdbcDbAccessProvider(JdbcConnectionSupport connectionSupport) {
        this.connectionSupport = connectionSupport;
    }

    @Override
    public boolean supports(DbAccessSourceType sourceType, DbAccessDbType dbType) {
        return sourceType == DbAccessSourceType.DATABASE && JdbcDatabaseProfile.supports(dbType);
    }

    @Override
    public DbAccessExecutor createExecutor(DbAccessContext context) throws DbAccessException {
        return new JdbcDbAccessExecutor(connectionSupport, context);
    }
}
