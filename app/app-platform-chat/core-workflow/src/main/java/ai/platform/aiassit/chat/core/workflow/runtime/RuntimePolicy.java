package ai.platform.aiassit.chat.core.workflow.runtime;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 节点运行期限制。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
@Data
public class RuntimePolicy implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int maxSteps = 8;

    private int maxToolCalls = 10;

    private int timeoutSeconds = 120;
}
