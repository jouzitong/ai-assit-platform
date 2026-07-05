package ai.platform.aiassit.conversation.workflow.tool;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具执行上下文。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
@Data
public class ToolExecutionContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String workflowCode;

    private String nodeCode;

    private String traceId;

    private Map<String, Object> attributes = new LinkedHashMap<>();
}
