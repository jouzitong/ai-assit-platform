package ai.platform.aiassit.chat.core.workflow.node;

import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
public interface IWorkflowNode {

    String type();

    NodeResult execute(WorkflowContext context, WorkflowNodeConfig nodeConfig);

    int order();

}
