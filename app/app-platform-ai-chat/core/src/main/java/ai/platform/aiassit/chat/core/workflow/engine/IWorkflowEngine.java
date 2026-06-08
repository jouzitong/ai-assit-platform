package ai.platform.aiassit.chat.core.workflow.engine;

import ai.platform.aiassit.chat.core.workflow.bean.WorkflowDefinition;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
public interface IWorkflowEngine {

    void run(WorkflowDefinition definition, WorkflowContext context);

}
