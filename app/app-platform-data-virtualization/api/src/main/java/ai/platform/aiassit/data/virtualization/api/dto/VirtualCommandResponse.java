package ai.platform.aiassit.data.virtualization.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VirtualCommandResponse {
    private String requestId;
    private String planId;
    private Integer affectedRows;
    private Boolean success;
    private Boolean partialSuccess = false;
    private Integer successfulTaskCount;
    private Integer failedTaskCount;
    private String transactionMode;
    private List<TaskResult> tasks = new ArrayList<>();

    @Data
    public static class TaskResult {
        private String taskId;
        private String bindingCode;
        private Boolean success;
        private Integer affectedRows;
        private String errorCode;
        private String errorMessage;
    }
}
