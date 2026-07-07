package ai.platform.aiassit.chat.workflow.data.entity.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill 目录配置。
 */
@Data
public class WorkflowSkillCatalogConfig {

    /**
     * Skill 摘要。
     */
    private String summary;

    /**
     * 默认可挂接阶段。
     */
    private List<String> supportedPhases = new ArrayList<>();

    /**
     * 默认配置模板。
     */
    private Map<String, Object> defaultOptions = new LinkedHashMap<>();

    /**
     * 扩展字段。
     */
    private Map<String, Object> ext = new LinkedHashMap<>();
}
