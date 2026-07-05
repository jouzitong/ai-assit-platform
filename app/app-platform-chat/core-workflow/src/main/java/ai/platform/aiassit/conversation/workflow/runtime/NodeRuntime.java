package ai.platform.aiassit.conversation.workflow.runtime;

import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.conversation.workflow.tool.ScopedToolSet;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 节点运行时入参。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
@Data
public class NodeRuntime implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private WorkflowNodeConfig node;

    private NodeContract contract;

    private ContextView contextView;

    private ScopedToolSet tools;

    private ExecutionHistory history;

    private RuntimePolicy policy;
}
