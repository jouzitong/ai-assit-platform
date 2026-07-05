package ai.platform.aiassit.chat.core.workflow.bean;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 引擎最终采用的流转决策。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
@Data
public class TransitionDecision implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private TransitionAction action;

    private String targetNodeId;

    private boolean acceptedProposal;

    private DecisionSource decisionSource;

    private String reason;

    private Double confidence;
}
