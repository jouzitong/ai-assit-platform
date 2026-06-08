package ai.platform.aiassit.chat.core.workflow.node;

import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
@Slf4j
public abstract class BaseWorkflowNode implements IWorkflowNode{

    @Override
    public NodeResult execute(WorkflowContext context) {
        return null;
    }

    @Override
    public int order() {
        return 100;
    }
}
