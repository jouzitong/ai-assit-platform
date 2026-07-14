package ai.platform.aiassit.data.virtualization.core.execution;

import ai.platform.aiassit.data.virtualization.core.plan.PhysicalExecutionPlan;
import ai.platform.aiassit.data.virtualization.spi.query.PhysicalQueryPort;
import ai.platform.aiassit.data.virtualization.spi.query.PhysicalQueryResult;
import org.springframework.stereotype.Component;

@Component
public class PhysicalTaskExecutor {
    private final PhysicalQueryPort queryPort;

    public PhysicalTaskExecutor(PhysicalQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    public PhysicalQueryResult execute(PhysicalExecutionPlan.PhysicalTask task) {
        return queryPort.query(task.queryCommand());
    }
}
