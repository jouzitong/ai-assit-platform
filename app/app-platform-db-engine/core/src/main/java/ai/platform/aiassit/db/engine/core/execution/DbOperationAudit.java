package ai.platform.aiassit.db.engine.core.execution;

import ai.platform.aiassit.db.engine.executor.spi.plan.DbQueryPlan;
import ai.platform.aiassit.db.engine.executor.spi.result.QueryResult;

/** 执行审计扩展点；实现可以写入数据库、Outbox 或消息系统。 */
public interface DbOperationAudit {

    void beforeExecute(DbExecutionContext context, DbQueryPlan plan);

    void afterSuccess(DbExecutionContext context, DbQueryPlan plan, QueryResult result);

    void afterFailure(DbExecutionContext context, DbQueryPlan plan, Throwable error);
}
