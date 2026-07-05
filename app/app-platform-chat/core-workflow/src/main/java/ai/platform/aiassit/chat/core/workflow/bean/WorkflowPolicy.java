package ai.platform.aiassit.chat.core.workflow.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工作流级运行策略。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowPolicy implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int maxTotalSteps = 30;

    private int maxNodeRetries = 3;

    private int maxWorkflowDurationSeconds = 300;

    private int maxNodeLoopPerPath = 5;

    public static WorkflowPolicy defaultPolicy() {
        return new WorkflowPolicy(30, 3, 300, 5);
    }
}
