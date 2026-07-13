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
        DbSqlDialect matched = null;
        for (DbSqlDialect dialect : dialects) {
            if (dialect.dbType() == dbType) {
                if (matched != null) {
                    throw new DbAccessException("数据库存在多个 SQL 方言: " + dbType);
                }
                matched = dialect;
            }
        }
        if (matched != null) {
            return matched;
        }
        throw new DbAccessException("未找到数据库 SQL 方言: " + dbType);
    }
}
