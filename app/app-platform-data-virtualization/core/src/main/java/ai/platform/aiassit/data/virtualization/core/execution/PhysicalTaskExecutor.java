package ai.platform.aiassit.data.virtualization.core.execution;

import ai.platform.aiassit.data.virtualization.core.plan.PhysicalExecutionPlan;
import ai.platform.aiassit.db.engine.core.execution.DbQueryExecutionPipeline;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbOperationType;
import ai.platform.aiassit.db.engine.executor.spi.plan.DbQueryPlan;
import ai.platform.aiassit.db.engine.executor.spi.result.QueryResult;
import org.springframework.stereotype.Component;

@Component
public class PhysicalTaskExecutor {
    private final DbQueryExecutionPipeline executionPipeline;

    public PhysicalTaskExecutor(DbQueryExecutionPipeline executionPipeline) {
        this.executionPipeline = executionPipeline;
    }

    public QueryResult execute(PhysicalExecutionPlan.PhysicalTask task) {
        DbQueryPlan plan = DbQueryPlan.builder()
                .operationType(DbOperationType.QUERY)
                .model(task.binding().physicalTableName())
                .statement(task.sql())
                .parameters(task.parameters())
                .maxRows(task.maxRows())
                .build();
        return executionPipeline.execute(task.binding().sourceKey(), plan);
    }
}
