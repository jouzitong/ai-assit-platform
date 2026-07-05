package ai.platform.aiassit.chat.core.workflow.engine.transition;

import ai.platform.aiassit.chat.core.workflow.bean.NodeExecutionResult;
import ai.platform.aiassit.chat.core.workflow.bean.TransitionDecision;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowDefinition;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowExecutionState;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowNodeConfig;

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
