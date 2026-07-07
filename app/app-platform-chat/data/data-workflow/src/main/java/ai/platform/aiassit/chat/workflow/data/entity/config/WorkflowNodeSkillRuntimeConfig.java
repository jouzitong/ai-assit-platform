package ai.platform.aiassit.chat.workflow.data.entity.config;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点 Skill 挂接配置。
 */
@Data
public class WorkflowNodeSkillRuntimeConfig {

    /**
     * 是否必选。
     */
    private Boolean required = Boolean.FALSE;

    /**
     * Skill 参数。
     */
    private Map<String, Object> options = new LinkedHashMap<>();

    /**
     * 扩展字段。
     */
    private Map<String, Object> ext = new LinkedHashMap<>();
}
