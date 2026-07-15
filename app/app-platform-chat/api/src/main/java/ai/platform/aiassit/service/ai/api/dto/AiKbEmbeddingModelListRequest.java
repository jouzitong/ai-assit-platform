package ai.platform.aiassit.service.ai.api.dto;

import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import lombok.Data;

import java.io.Serializable;

/** 查询知识库 Provider 可用 Embedding 模型的请求。 */
@Data
public class AiKbEmbeddingModelListRequest implements Serializable {

    /** 知识库提供方类型；未传时默认 RAGFlow。 */
    private AiKnowledgeClientType clientType = AiKnowledgeClientType.RAGFLOW;

    /** Provider 调用上下文；认证信息仅由服务端注入。 */
    private RequestMeta meta = new RequestMeta();
}
