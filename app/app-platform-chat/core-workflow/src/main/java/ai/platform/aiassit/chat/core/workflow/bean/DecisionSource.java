package ai.platform.aiassit.chat.core.workflow.bean;

/**
 * 流转决策来源。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
public enum DecisionSource {

    NODE_PROPOSAL,
    DEFAULT_EDGE,
    WORKFLOW_POLICY,
    SYSTEM_GUARD,
    MANUAL_OVERRIDE

}
