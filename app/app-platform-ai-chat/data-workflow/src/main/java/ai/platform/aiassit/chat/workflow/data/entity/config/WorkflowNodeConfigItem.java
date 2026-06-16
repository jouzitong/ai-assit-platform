package ai.platform.aiassit.chat.workflow.data.entity.config;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点配置项定义。
 */
@Data
public class WorkflowNodeConfigItem {

    private String code;

    private String name;

    private String type;

    private String summary;

    private Boolean enabled = Boolean.TRUE;

    private Map<String, Object> ext = new LinkedHashMap<>();
}
