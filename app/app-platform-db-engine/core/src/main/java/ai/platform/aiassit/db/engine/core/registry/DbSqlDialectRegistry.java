package ai.platform.aiassit.db.engine.core.registry;

import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.provider.DbSqlDialect;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DbSqlDialectRegistry {

    private final List<DbSqlDialect> dialects;

    public DbSqlDialectRegistry(List<DbSqlDialect> dialects) {
        this.dialects = dialects;
    }

    public DbSqlDialect get(DbAccessDbType dbType) throws DbAccessException {
        for (DbSqlDialect dialect : dialects) {
            if (dialect.dbType() == dbType) {
                return dialect;
            }
        }
        throw new DbAccessException("未找到数据库 SQL 方言: " + dbType);
    }
}
