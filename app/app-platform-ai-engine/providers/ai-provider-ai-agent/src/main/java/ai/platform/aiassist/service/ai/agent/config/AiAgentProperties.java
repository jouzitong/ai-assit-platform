package ai.platform.aiassist.service.ai.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI Agent 提供方配置。
 */
@Data
@ConfigurationProperties(prefix = "ai.provider.ai-agent")
public class AiAgentProperties {

    /** 是否启用 AI Agent 提供方 */
    private boolean enabled = false;

    /** OpenAI API Key，默认留空，由业务配置显式注入 */
    private String apiKey = "";

    /** OpenAI 兼容 Base URL，留空时使用官方默认地址 */
    private String baseUrl = "";

    /** 默认模型 */
    private String defaultModel = "gpt-5.5";

    /** Python 命令 */
    private String pythonCommand = "python3";

    /** 自定义 Python 脚本路径，建议在部署时指向外部 Python 项目入口 */
    private String scriptPath = "";

    /** Python 执行工作目录，留空时使用应用当前目录 */
    private String workingDirectory = "";

    /** 调用超时（毫秒） */
    private Integer timeoutMs = 60000;
}
