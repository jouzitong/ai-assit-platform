package ai.platform.aiassit.db.engine.core.execution;

import ai.platform.aiassit.db.engine.executor.spi.plan.DbQueryPlan;
import ai.platform.aiassit.db.engine.executor.spi.result.QueryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 当前审计占位实现：仅记录不含 SQL 参数和值的执行摘要。 */
@Slf4j
@Component
public class LoggingDbOperationAudit implements DbOperationAudit {

    @Override
    public void beforeExecute(DbExecutionContext context, DbQueryPlan plan) {
        log.debug("DB execution started, requestId={}, sourceKey={}, model={}, operation={}",
                context.requestId(), context.sourceKey(), context.model(), context.operationType());
    }

    @Override
    public void afterSuccess(DbExecutionContext context, DbQueryPlan plan, QueryResult result) {
        log.debug("DB execution succeeded, requestId={}, rowCount={}, executionMs={}",
                context.requestId(), result.getRowCount(), result.getExecutionMs());
    }

    @Override
    public void afterFailure(DbExecutionContext context, DbQueryPlan plan, Throwable error) {
        log.warn("DB execution failed, requestId={}, model={}, operation={}, error={}",
                context.requestId(), context.model(), context.operationType(), error.getMessage());
    }
}
