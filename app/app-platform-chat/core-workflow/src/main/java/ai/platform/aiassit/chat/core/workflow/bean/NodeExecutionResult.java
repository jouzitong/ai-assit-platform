package ai.platform.aiassit.chat.core.workflow.bean;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 新版节点执行结果。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
@Data
public class NodeExecutionResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String nodeId;

    private boolean success = true;

    private String status;

    private String summary;

    private Double confidence;

    private String payloadType;

    private String payloadVersion = "1.0";

    private Object payload;

    private List<NodeArtifactRef> artifacts = new ArrayList<>();

    private TransitionProposal transitionProposal;

    private String errorMessage;

    private Map<String, Object> metadata = new LinkedHashMap<>();

    public static NodeExecutionResult success(String nodeId, String status) {
        NodeExecutionResult result = new NodeExecutionResult();
        result.setNodeId(nodeId);
        result.setStatus(status);
        result.setSuccess(true);
        return result;
    }

    public static NodeExecutionResult fail(String nodeId, String errorMessage) {
        NodeExecutionResult result = new NodeExecutionResult();
        result.setNodeId(nodeId);
        result.setStatus("FAILED");
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }
}
