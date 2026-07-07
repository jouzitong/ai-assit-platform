package ai.platform.aiassit.chat.workflow.data.entity.config;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程字段定义。
 */
@Data
public class WorkflowFieldDefinition {

    /**
     * 字段编码。
     */
    private String fieldCode;

    /**
     * 字段名称。
     */
    private String fieldName;

    /**
     * 字段路径。
     */
    private String fieldPath;

    /**
     * 数据类型。
     */
    private String dataType;

    /**
     * 是否必填。
     */
    private Boolean required = Boolean.FALSE;

    /**
     * 来源引用。
     */
    private String sourceRef;

    /**
     * 字段约束与 schema 片段。
     */
    private Map<String, Object> schema = new LinkedHashMap<>();

    /**
     * 扩展字段。
     */
    private Map<String, Object> ext = new LinkedHashMap<>();
}
