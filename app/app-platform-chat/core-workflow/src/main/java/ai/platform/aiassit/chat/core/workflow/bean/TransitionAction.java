package ai.platform.aiassit.chat.core.workflow.bean;

/**
 * 节点流转动作。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
public enum TransitionAction {

    GOTO,
    CONTINUE,
    RETRY,
    COMPLETE,
    WAIT,
    CLARIFY,
    FAIL

}
