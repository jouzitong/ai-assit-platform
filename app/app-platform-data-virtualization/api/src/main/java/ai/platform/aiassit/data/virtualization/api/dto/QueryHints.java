package ai.platform.aiassit.data.virtualization.api.dto;

import lombok.Data;

@Data
public class QueryHints {
    private Integer maxPhysicalTasks = 16;
    private Integer maxScanRows = 10000;
    private Integer timeoutMs = 30000;
    private Boolean allowLocalTransform = true;
}
