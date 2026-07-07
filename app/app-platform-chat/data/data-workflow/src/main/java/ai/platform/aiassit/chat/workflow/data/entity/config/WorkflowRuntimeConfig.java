package ai.platform.aiassit.chat.workflow.data.entity.config;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程运行配置。
 */
@Data
public class WorkflowRuntimeConfig {

    /**
     * 起始节点编码。
     */
    private String startNodeCode;

    /**
     * 流程级参数，例如超时、开关、路由策略。
     */
    private Map<String, Object> options = new LinkedHashMap<>();

    /**
     * 扩展字段。
     */
    private Map<String, Object> ext = new LinkedHashMap<>();
}
