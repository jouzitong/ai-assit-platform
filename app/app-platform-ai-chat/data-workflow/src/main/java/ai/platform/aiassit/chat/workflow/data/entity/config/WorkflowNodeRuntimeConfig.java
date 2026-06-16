package ai.platform.aiassit.chat.workflow.data.entity.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点运行配置。
 */
@Data
public class WorkflowNodeRuntimeConfig {

    /**
     * 节点摘要。
     */
    private String summary;

    /**
     * 执行模式。
     */
    private String executeMode;

    /**
     * 输入定义。
     */
    private List<WorkflowFieldDefinition> inputDefinitions = new ArrayList<>();

    /**
     * 输出定义。
     */
    private List<WorkflowFieldDefinition> outputDefinitions = new ArrayList<>();

    /**
     * 节点参数，如 prompt、规则、回跳策略等。
     */
    private Map<String, Object> options = new LinkedHashMap<>();

    /**
     * 扩展字段。
     */
    private Map<String, Object> ext = new LinkedHashMap<>();
}
