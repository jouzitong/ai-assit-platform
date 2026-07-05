package ai.platform.aiassit.chat.core.workflow.tool;

/**
 * 工作流工具统一接口。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
public interface WorkflowTool<I, O> {

    String code();

    O execute(I input, ToolExecutionContext context);
}
