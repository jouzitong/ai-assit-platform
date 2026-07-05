package ai.platform.aiassit.chat.core.workflow.bean;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作流运行时状态快照。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
@Data
public class WorkflowExecutionState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String workflowCode;

    private String currentNodeId;

    private int totalSteps;

    private Instant startedAt = Instant.now();

    private Map<String, Integer> nodeAttempts = new LinkedHashMap<>();

    public int incrementNodeAttempt(String nodeId) {
        int nextAttempt = nodeAttempts.getOrDefault(nodeId, 0) + 1;
        nodeAttempts.put(nodeId, nextAttempt);
        return nextAttempt;
    }

    public int getNodeAttempt(String nodeId) {
        return nodeAttempts.getOrDefault(nodeId, 0);
    }
}
