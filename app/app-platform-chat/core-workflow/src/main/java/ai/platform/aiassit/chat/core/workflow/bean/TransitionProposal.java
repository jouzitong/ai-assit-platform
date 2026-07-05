package ai.platform.aiassit.chat.core.workflow.bean;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点执行后给出的流转建议。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
@Data
public class TransitionProposal implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private TransitionAction action = TransitionAction.CONTINUE;

    private String targetNodeId;

    private String reasonCode;

    private String reason;

    private Double confidence;

    private Map<String, Object> instructions = new LinkedHashMap<>();

    public static TransitionProposal gotoNode(String targetNodeId, String reasonCode, String reason) {
        TransitionProposal proposal = new TransitionProposal();
        proposal.setAction(TransitionAction.GOTO);
        proposal.setTargetNodeId(targetNodeId);
        proposal.setReasonCode(reasonCode);
        proposal.setReason(reason);
        return proposal;
    }

    public static TransitionProposal complete(String reason) {
        TransitionProposal proposal = new TransitionProposal();
        proposal.setAction(TransitionAction.COMPLETE);
        proposal.setReason(reason);
        return proposal;
    }
}
