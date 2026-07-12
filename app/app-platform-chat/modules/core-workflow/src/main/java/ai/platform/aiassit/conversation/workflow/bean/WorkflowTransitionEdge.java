package ai.platform.aiassit.conversation.workflow.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 工作流允许的节点流转边。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTransitionEdge implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String fromNodeId;

    private String toNodeId;

    private TransitionAction action = TransitionAction.CONTINUE;

    private boolean defaultEdge;

    private String conditionCode;

    private Map<String, Object> metadata = new LinkedHashMap<>();

    public boolean matches(String sourceNodeId, TransitionAction proposalAction, String targetNodeId) {
        if (!Objects.equals(fromNodeId, sourceNodeId)) {
            return false;
        }
        if (proposalAction != null && action != null && proposalAction != action && proposalAction != TransitionAction.GOTO) {
            return false;
        }
        return targetNodeId == null || Objects.equals(toNodeId, targetNodeId);
    }
}
