package ai.platform.aiassit.chat.core.workflow.node;

import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流基础节点。
 *
 * <p>当前节点链路与职责约定如下：</p>
 * <pre>
 * 用户问题
 * ↓
 * ChatMessageNode     初始化/加载会话上下文
 * ↓
 * QueryPlanningNode   提炼用户意图、生成执行规划
 * ↓
 * KnowledgeSearchNode 结合知识库与模型配置补充上下文
 * ↓
 * SqlGenerateNode     生成候选 SQL
 * ↓                  ↑
 * SqlValidateNode ---- SQL 不合法时回跳重新生成
 * ↓
 * SqlExecuteNode      执行或显式降级执行结果
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

    protected abstract NodeResult doExecute(WorkflowContext context);

    @Override
    public NodeResult execute(WorkflowContext context) {
        return doExecute(context);
    }

    @Override
    public int order() {
        return 100;
    }
}
