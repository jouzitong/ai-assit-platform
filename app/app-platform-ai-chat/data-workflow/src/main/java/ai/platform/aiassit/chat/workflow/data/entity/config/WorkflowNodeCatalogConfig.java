package ai.platform.aiassit.chat.workflow.data.entity.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点目录配置。
 */
@Data
public class WorkflowNodeCatalogConfig {

    /**
     * 节点摘要。
     */
    private String summary;

    /**
     * 默认执行模式。
     */
    private String executeMode;

    /**
     * 默认输入定义。
     */
    private List<WorkflowFieldDefinition> inputDefinitions = new ArrayList<>();

    /**
     * 默认输出定义。
     */
    private List<WorkflowFieldDefinition> outputDefinitions = new ArrayList<>();

    /**
     * 默认扩展配置。
     */
    private Map<String, Object> ext = new LinkedHashMap<>();
}
