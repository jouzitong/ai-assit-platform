package ai.platform.aiassit.chat.core.workflow.context;

import ai.platform.aiassit.service.ai.api.dto.ChatRequest;
import ai.platform.aiassit.service.ai.api.dto.ChatResponse;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单个工作流节点的结果上下文。
 *
 * @author zhouzhitong
 * @since 2026/6/22
 */
@Data
public class WorkflowNodeResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 节点编码。
     */
    private String nodeCode;

    /**
     * 节点执行状态。
     */
    private String status;

    /**
     * 节点调用模型时的请求快照。
     */
    private ChatRequest request;

    /**
     * 节点调用模型时的响应快照。
     */
    private ChatResponse response;

    /**
     * 节点核心输出结果。
     */
    private Map<String, Object> outputs = new LinkedHashMap<>();

    /**
     * 节点附加元信息。
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
