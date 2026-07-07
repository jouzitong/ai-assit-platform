package ai.platform.aiassit.chat.workflow.data.entity.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程目录配置。
 */
@Data
public class WorkflowCatalogConfig {

    /**
     * 页面路由或业务短键，例如 query。
     */
    private String routeKey;

    /**
     * 场景说明。
     */
    private String sceneDesc;

    /**
     * 标签。
     */
    private List<String> tags = new ArrayList<>();

    /**
     * 扩展字段。
     */
    private Map<String, Object> ext = new LinkedHashMap<>();
}
