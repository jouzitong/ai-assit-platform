package ai.platform.aiassit.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 知识库 Provider 可用于向量化的模型选项。 */
@Data
public class AiKbEmbeddingModelDTO implements Serializable {

    /** 页面和 Dataset 创建请求使用的模型值，优先为 Provider 模型 ID。 */
    private String value;

    /** Provider 侧模型 ID。 */
    private String modelId;

    /** 模型名称。 */
    private String name;

    /** Provider 名称。 */
    private String providerName;

    /** Provider 实例名称。 */
    private String instanceName;

    /** 模型类型。 */
    private List<String> modelTypes = new ArrayList<>();

    /** 是否启用。 */
    private Boolean enabled;

    /** Provider 返回的其余字段。 */
    private Map<String, Object> ext = new LinkedHashMap<>();
}
