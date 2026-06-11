package ai.platform.aiassit.chat.core.workflow.engine;

import ai.platform.aiassit.chat.core.workflow.bean.WorkflowDefinition;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;

/**
 * 工作流引擎接口。
 *
 * <p>用于根据工作流定义和运行上下文，统一调度并执行工作流中的节点逻辑。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
public interface IWorkflowEngine {

    /**
     * 执行工作流。
     *
     * @param definition 工作流定义，描述工作流包含的节点、流转关系以及执行规则
     * @param context 工作流上下文，用于在工作流执行过程中传递输入参数、中间结果和运行状态
     */
    void run(WorkflowDefinition definition, WorkflowContext context);

}
