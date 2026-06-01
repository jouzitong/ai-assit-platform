package ai.platform.aiassist.service.ai.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import ai.platform.aiassist.service.ai.api.enums.ProviderType;

/**
 * AI Core 路由配置。
 */
@Data
@ConfigurationProperties(prefix = "ai.core")
public class AiCoreProperties {

    /** 默认提供方 */
    private ProviderType defaultProvider = ProviderType.DASHSCOPE;
    /** 默认对话模型 */
    private String defaultChatModel = "qwen-plus";
    /** 默认向量模型 */
    private String defaultEmbeddingModel = "text-embedding-v3";
    /** 默认重排模型 */
    private String defaultRerankModel = "gte-rerank-v2";

    /**
     * 是否严格要求请求显式指定 provider。
     * true: request.provider 为空直接报错；
     * false: 使用 defaultProvider 兜底。
     */
    private boolean strictProvider = false;
}
