package ai.platform.aiassit.execution.properties;

import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI Core 路由配置。
 */
@Data
@ConfigurationProperties(prefix = "ai.core")
@Component
public class AiCoreProperties {

    /** 默认对话客户端类型 */
    private AiChatClientType defaultChatClientType = AiChatClientType.SPRING_AI;
    /** 默认知识库客户端类型 */
    private AiKnowledgeClientType defaultKnowledgeClientType = AiKnowledgeClientType.BAILIAN;
    /** 默认对话模型 */
    private String defaultChatModel = "qwen3.6-plus";
    /** 默认向量模型 */
    private String defaultEmbeddingModel = "text-embedding-v3";
    /** 默认重排模型 */
    private String defaultRerankModel = "gte-rerank-v2";

    /**
     * 是否严格要求请求显式指定客户端类型。
     * true: request.clientType 为空直接报错；
     * false: 使用对应场景的默认客户端类型兜底。
     */
    private boolean strictClientType = false;
}
