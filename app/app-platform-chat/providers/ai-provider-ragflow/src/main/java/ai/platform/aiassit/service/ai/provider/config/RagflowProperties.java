package ai.platform.aiassit.service.ai.provider.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** RAGFlow 提供方配置。 */
@Data
@ConfigurationProperties(prefix = "ai.provider.ragflow")
public class RagflowProperties {

    /** 是否启用 RAGFlow 知识库提供方。 */
    private boolean enabled;

    /** RAGFlow 服务根地址。 */
    private String baseUrl;

    /** RAGFlow API Key。 */
    private String apiKey;

    /** HTTP 调用超时（毫秒）。 */
    private Integer timeoutMs = 30000;
}
