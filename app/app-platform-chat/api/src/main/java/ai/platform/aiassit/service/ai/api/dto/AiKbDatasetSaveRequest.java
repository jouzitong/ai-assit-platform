package ai.platform.aiassit.service.ai.api.dto;

import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 创建或更新知识库 Provider Dataset 的请求。
 *
 * <p>字段与 RAGFlow Dataset API 对齐。使用 {@code pipelineId} 时不应同时传递
 * {@code chunkMethod} 和 {@code parserConfig}。</p>
 */
@Data
public class AiKbDatasetSaveRequest implements Serializable {

    /** 知识库提供方类型；未传时默认 RAGFlow。 */
    private AiKnowledgeClientType clientType = AiKnowledgeClientType.RAGFLOW;

    /** Dataset 名称；创建时必填。 */
    private String name;

    /** Dataset 描述。 */
    private String description;

    /** 向量化模型标识。 */
    private String embeddingModel;

    /** Provider 侧权限，例如 team。 */
    private String permission;

    /** 内置切片方式，例如 naive、qa、table。 */
    private String chunkMethod;

    /** 内置切片配置。 */
    private Map<String, Object> parserConfig = new LinkedHashMap<>();

    /** 自定义解析类型；与 pipelineId 配套使用。 */
    private String parseType;

    /** 自定义 ingestion pipeline 标识。 */
    private String pipelineId;

    /** RAGFlow 的其他可选 Dataset 配置，例如 pagerank。 */
    private Map<String, Object> ext = new LinkedHashMap<>();

    /** Provider 调用上下文；认证信息仅由服务端注入。 */
    private RequestMeta meta = new RequestMeta();
}
