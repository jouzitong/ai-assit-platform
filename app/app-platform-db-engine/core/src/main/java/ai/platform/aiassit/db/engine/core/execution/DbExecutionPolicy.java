package ai.platform.aiassit.db.engine.core.execution;

import ai.platform.aiassit.db.engine.executor.spi.plan.DbQueryPlan;

/** 在 SQL 渲染前校验或改写执行计划的扩展点。 */
public interface DbExecutionPolicy {

    void apply(DbExecutionContext context, DbQueryPlan plan);
}
