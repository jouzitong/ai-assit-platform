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
    private boolean enabled = true;

    /** 千问 API Key */
    private String apiKey;

    /** 千问服务地址，默认 DashScope 兼容地址 */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode";

    /** 默认模型 */
    private String defaultModel = "qwen-plus";

    /** 调用超时（毫秒） */
    private Integer timeoutMs = 30000;

    /** 百炼知识库接入点 */
    private String bailianEndpoint = "bailian.cn-beijing.aliyuncs.com";

    /** 默认业务空间 ID */
    private String workspaceId = "w05enpcxa4";

    /** 默认知识库类目 ID */
    private String categoryId = "default";

    /** 默认文件解析器 */
    private String parser = "DASHSCOPE_DOCMIND";

    /** 知识库源类型 */
    private String sourceType = "DATA_CENTER_FILE";

    /** 知识库结构类型 */
    private String structureType = "unstructured";

    /** 知识库存储类型 */
    private String sinkType = "BUILT_IN";

    /** 轮询索引任务间隔（毫秒） */
    private Integer kbPollIntervalMs = 1000;

    /** 等待索引任务完成超时（毫秒） */
    private Integer kbJobTimeoutMs = 120000;

    /** 阿里云 AccessKey ID，用于百炼知识库管理 */
    private String accessKeyId;

    /** 阿里云 AccessKey Secret，用于百炼知识库管理 */
    private String accessKeySecret;
}
