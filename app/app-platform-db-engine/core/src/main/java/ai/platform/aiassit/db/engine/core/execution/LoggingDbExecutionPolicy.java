package ai.platform.aiassit.db.engine.core.execution;

import ai.platform.aiassit.db.engine.executor.spi.plan.DbQueryPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 当前权限策略占位实现；后续可在此替换为资源、字段和行级策略。 */
@Slf4j
@Component
public class LoggingDbExecutionPolicy implements DbExecutionPolicy {

    @Override
    public void apply(DbExecutionContext context, DbQueryPlan plan) {
        log.debug("DB execution policy placeholder, requestId={}, userId={}, model={}, operation={}",
                context.requestId(), context.userId(), context.model(), context.operationType());
    }
}
