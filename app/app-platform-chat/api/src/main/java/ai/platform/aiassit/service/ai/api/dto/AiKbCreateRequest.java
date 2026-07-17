package ai.platform.aiassit.service.ai.api.dto;

import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class AiKbCreateRequest implements Serializable {

    /** 用户自定义的本地知识库业务编码，创建后不可修改。 */
    private String kbCode;

    /** 知识库名称。 */
    private String kbName;

    /** 向量化模型标识。 */
    private String embeddingModel;

    /** RAGFlow 分片方式；未传时按无需切片处理。 */
    private String chunkMethod = "one";

    /** RAGFlow 分片配置。 */
    private Map<String, Object> parserConfig;

    /** 知识库客户端类型。 */
    private AiKnowledgeClientType clientType;

    /** AI 侧知识库 ID。 */
    private String providerKbId;

    /** 是否启用。 */
    private Boolean enabled;

    /** 标签列表。 */
    private List<String> tags;

    /** 知识库请求地址。 */
    private String url;

    /** 扩展参数。 */
    private Map<String, Object> ext;
}
