package ai.platform.aiassit.data.virtualization.core.execution;

import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.core.plan.PhysicalExecutionPlan;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PhysicalExecutionEngine {
    private final ExecutionOrchestrator orchestrator;
    private final FieldTransformExecutor transformExecutor;
    private final FilterEvaluator filterEvaluator;

    public PhysicalExecutionEngine(
            ExecutionOrchestrator orchestrator,
            FieldTransformExecutor transformExecutor,
            FilterEvaluator filterEvaluator
    ) {
        this.orchestrator = orchestrator;
        this.transformExecutor = transformExecutor;
        this.filterEvaluator = filterEvaluator;
    }

    public ExecutionRows execute(PhysicalExecutionPlan plan) {
        long started = System.currentTimeMillis();
        List<ExecutionOrchestrator.TaskOutput> outputs = orchestrator.execute(plan);
        if (outputs.stream().allMatch(output -> output.task().countOnly())) {
            long total = outputs.stream().mapToLong(output -> count(output.result().getRows())).sum();
            return new ExecutionRows(new ArrayList<>(), total, outputs.size(), System.currentTimeMillis() - started);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ExecutionOrchestrator.TaskOutput output : outputs) {
            List<Map<String, Object>> physicalRows = output.result().getRows() == null ? List.of() : output.result().getRows();
            if (physicalRows.size() > plan.logicalPlan().maxScanRows()) {
                throw new VirtualDataException("PLAN_BUDGET_EXCEEDED", "物理扫描达到 maxScanRows，拒绝返回可能不完整的结果");
            }
            for (Map<String, Object> physicalRow : physicalRows) {
                Map<String, Object> virtualRow = transformExecutor.readRow(plan.snapshot(), output.task(), physicalRow);
                if (filterEvaluator.test(plan.logicalPlan().filter(), virtualRow)) {
                    rows.add(virtualRow);
                    if (rows.size() > plan.logicalPlan().maxScanRows()) {
                        throw new VirtualDataException("PLAN_BUDGET_EXCEEDED", "合并结果超过 maxScanRows 预算");
                    }
                }
            }
        }
        return new ExecutionRows(rows, (long) rows.size(), outputs.size(), System.currentTimeMillis() - started);
    }

    private long count(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return 0;
        Map<String, Object> row = rows.get(0);
        Object value = row.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase("__count"))
                .map(Map.Entry::getValue).findFirst().orElse(0);
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    public record ExecutionRows(List<Map<String, Object>> rows, long total, int physicalTaskCount, long executionMs) {
    }
}
