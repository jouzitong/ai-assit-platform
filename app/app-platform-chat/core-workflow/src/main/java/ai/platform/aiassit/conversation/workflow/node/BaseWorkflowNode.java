package ai.platform.aiassit.conversation.workflow.node;

import ai.platform.aiassit.conversation.workflow.bean.NodeResult;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.conversation.workflow.capability.WorkflowPromptContextCapabilityExecutor;
import ai.platform.aiassit.conversation.workflow.context.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 工作流基础节点。
 *
 * <p>当前节点链路与职责约定如下：</p>
 * <pre>
 * 用户问题
 * ↓
 * 会话初始化          初始化/加载会话上下文
 * ↓
 * QueryPlanningNode   提炼用户意图、生成执行规划
 * ↓
 * SqlPreGenerateNode  生成预生成结果与伪 SQL
 * ↓
 * RenderNode          组织最终回复并落库
 * </pre>
 *
 * <p>各节点只负责自己的阶段产物，统一通过 {@link WorkflowContext} 传递上下文。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
@Slf4j
public abstract class BaseWorkflowNode implements IWorkflowNode {

    @Autowired
    private WorkflowPromptContextCapabilityExecutor capabilityExecutor;

    protected void beforeExecute(WorkflowContext context, WorkflowNodeConfig nodeConfig) {
    }

    protected abstract NodeResult doExecute(WorkflowContext context);

    @Override
    public NodeResult execute(WorkflowContext context, WorkflowNodeConfig nodeConfig) {
        beforeExecute(context, nodeConfig);

        NodeResult capabilityResult = capabilityExecutor.execute(context, nodeConfig);
        if (!capabilityResult.isSuccess()) {
            return capabilityResult;
        }

        return doExecute(context);
    }

    @Override
    public int order() {
        return 100;
    }
}
