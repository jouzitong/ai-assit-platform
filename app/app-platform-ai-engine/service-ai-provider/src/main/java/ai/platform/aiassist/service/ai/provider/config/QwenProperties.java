package ai.platform.aiassist.service.ai.provider.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 千问提供方配置。
 */
@Data
@ConfigurationProperties(prefix = "ai.provider.qwen")
public class QwenProperties {

    /** 是否启用千问提供方 */
    private boolean enabled = false;

    /** 千问 API Key */
    private String apiKey;

    /** 千问服务地址，默认 DashScope 兼容地址 */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode";

    /** 默认模型 */
    private String defaultModel = "qwen-plus";

    /** 调用超时（毫秒） */
    private Integer timeoutMs = 30000;
}
