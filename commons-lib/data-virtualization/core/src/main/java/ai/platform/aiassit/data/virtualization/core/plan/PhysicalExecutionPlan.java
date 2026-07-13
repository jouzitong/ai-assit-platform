package ai.platform.aiassit.data.virtualization.core.plan;

import ai.platform.aiassit.data.virtualization.core.catalog.CatalogSnapshot;

import java.util.List;
import java.util.Map;

public record PhysicalExecutionPlan(
        String planId,
        CatalogSnapshot snapshot,
        VirtualLogicalPlan logicalPlan,
        List<PhysicalTask> tasks,
        List<String> warnings
) {
    public PhysicalExecutionPlan {
        tasks = List.copyOf(tasks);
        warnings = List.copyOf(warnings);
    }

    public record PhysicalTask(
            String taskId,
            CatalogSnapshot.Binding binding,
            String dbType,
            String sql,
            List<Object> parameters,
            int maxRows,
            boolean filterPushed,
            boolean countOnly,
            String routeReason,
            List<CatalogSnapshot.TransformRule> transformRules,
            Map<Long, String> physicalFieldAliases
    ) {
        public PhysicalTask {
            parameters = List.copyOf(parameters);
            transformRules = List.copyOf(transformRules);
            physicalFieldAliases = Map.copyOf(physicalFieldAliases);
        }
    }
}
