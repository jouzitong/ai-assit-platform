package ai.platform.aiassit.db.engine.executor.spi.provider;

import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.BoundSql;
import ai.platform.aiassit.db.engine.executor.spi.plan.DbQueryPlan;

/** 将数据库无关的执行计划渲染为特定数据库可执行 SQL。 */
public interface DbSqlDialect {

    DbAccessDbType dbType();

    BoundSql render(DbQueryPlan plan) throws DbAccessException;
}
