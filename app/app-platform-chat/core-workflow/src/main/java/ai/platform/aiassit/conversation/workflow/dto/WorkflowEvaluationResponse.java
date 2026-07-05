package ai.platform.aiassit.conversation.workflow.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作流结果评估响应。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
@Data
public class WorkflowEvaluationResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Boolean passed;

    private String retryNodeCode;

    private String reasonCode;

    private String reason;

    private Double confidence;

    private Boolean clarificationNeeded;

    private String clarificationQuestion;

    private List<String> missingCapabilities = new ArrayList<>();

    private List<String> importantInfos = new ArrayList<>();

    private String requestId;

    private String model;

    private String rawOutput;
}
