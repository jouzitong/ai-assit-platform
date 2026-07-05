package ai.platform.aiassit.conversation.workflow.engine.transition;

import ai.platform.aiassit.conversation.workflow.bean.NodeExecutionResult;
import ai.platform.aiassit.conversation.workflow.bean.TransitionDecision;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowDefinition;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowExecutionState;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeConfig;

/**
 * 工作流流转决策器。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
public interface TransitionResolver {

    TransitionDecision resolve(WorkflowDefinition definition,
                               WorkflowExecutionState state,
                               WorkflowNodeConfig currentNode,
                               NodeExecutionResult result);
}
