package ai.platform.aiassit.data.virtualization.core.plan;

import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualAggregate;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualPage;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualSort;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.ConsistencyLevel;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.QueryType;

import java.util.List;
import java.util.Set;

public record VirtualLogicalPlan(
        String entityCode,
        long catalogVersion,
        QueryType queryType,
        List<String> projections,
        Set<String> requiredFields,
        FilterNode filter,
        List<String> relationCodes,
        List<VirtualAggregate> aggregates,
        List<String> groupBy,
        List<VirtualSort> sorts,
        VirtualPage page,
        ConsistencyLevel consistency,
        int maxPhysicalTasks,
        int maxScanRows,
        int timeoutMs,
        boolean allowLocalTransform
) {
}
