package ai.platform.aiassit.conversation.workflow.node;

import ai.platform.aiassit.conversation.workflow.bean.NodeResult;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
public interface IWorkflowNode {

    String code();

    NodeResult execute(ConversationRuntimeContext context, WorkflowNodeConfig nodeConfig);

    int order();

}
