package ai.platform.aiassit.conversation.workflow.context;

import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作流结果上下文。
 *
 * <p>统一记录流程执行过程中各节点的结果产物，支持动态流程节点扩展。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/22
 */
@Data
public class WorkflowResultContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前工作流轮次信息。
     */
    private AiChatRoundDTO round;

    /**
     * 节点结果集合，key 为节点编码，value 为节点结果上下文。
     */
    private Map<String, WorkflowNodeResult> nodeResults = new LinkedHashMap<>();
}
