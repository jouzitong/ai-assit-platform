package ai.platform.aiassit.data.virtualization.core.execution;

import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.core.plan.PhysicalExecutionPlan;
import ai.platform.aiassit.db.engine.executor.spi.result.QueryResult;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Component
public class ExecutionOrchestrator {
    private final PhysicalTaskExecutor taskExecutor;
    private final Executor executor;

    public ExecutionOrchestrator(
            PhysicalTaskExecutor taskExecutor,
            @Qualifier("virtualDataTaskExecutor") Executor executor
    ) {
        this.taskExecutor = taskExecutor;
        this.executor = executor;
    }

    public List<TaskOutput> execute(PhysicalExecutionPlan plan) {
        List<CompletableFuture<TaskOutput>> futures = plan.tasks().stream().map(task ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return new TaskOutput(task, taskExecutor.execute(task));
                    } catch (RuntimeException ex) {
                        throw new VirtualDataException("PHYSICAL_TASK_FAILED", "物理任务执行失败: " + task.taskId(), ex);
                    }
                }, executor)
                        .orTimeout(plan.logicalPlan().timeoutMs(), TimeUnit.MILLISECONDS)
        ).toList();
        List<TaskOutput> outputs = new ArrayList<>();
        try {
            for (CompletableFuture<TaskOutput> future : futures) outputs.add(future.join());
            return outputs;
        } catch (CompletionException ex) {
            futures.forEach(future -> future.cancel(true));
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof VirtualDataException virtualDataException) throw virtualDataException;
            throw new VirtualDataException("PHYSICAL_TASK_FAILED", "物理任务执行失败", cause);
        }
    }

    public record TaskOutput(PhysicalExecutionPlan.PhysicalTask task, QueryResult result) {
    }
}
