package ai.platform.aiassit.data.virtualization.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VirtualExplainResponse {
    private String planId;
    private String entityCode;
    private Long catalogVersion;
    private List<Task> tasks = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class Task {
        private String taskId;
        private String bindingCode;
        private String sourceKey;
        private String physicalTable;
        private List<String> physicalColumns = new ArrayList<>();
        private List<String> transformRules = new ArrayList<>();
        private String routeReason;
    }
}
