package ai.platform.aiassit.conversation.workflow.runtime;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 节点运行时可见的简化执行历史。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
@Data
public class ExecutionHistory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<String> completedNodeIds = new ArrayList<>();

    private List<String> recentErrors = new ArrayList<>();
}
