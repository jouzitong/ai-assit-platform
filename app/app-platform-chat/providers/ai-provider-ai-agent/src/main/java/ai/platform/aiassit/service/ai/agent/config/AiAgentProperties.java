package ai.platform.aiassit.service.ai.agent.config;

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

    /** Agent 面向用户输出的默认语言；可由单次运行上下文中的 locale 覆盖 */
    private String responseLanguage = "zh-CN";

    /** Python 命令；留空时优先使用 Worker 项目的 .venv，找不到再回退到 python3 */
    private String pythonCommand = "";

    /** 自定义 Python 脚本路径，建议在部署时指向外部 Python 项目入口 */
    private String scriptPath = "";

    /** Python 执行工作目录，留空时使用应用当前目录 */
    private String workingDirectory = "";

    /** Node.js 命令，用于 OPENAI_AGENTS_TYPESCRIPT runtime */
    private String nodeCommand = "node";

    /** 自定义 TypeScript worker bundle 路径 */
    private String typescriptScriptPath = "";

    /** TypeScript worker 执行工作目录，留空时复用应用当前目录 */
    private String typescriptWorkingDirectory = "";

    /** 仅用于部署探针和离线验证；启用后 worker 只编译 Snapshot，不请求模型 */
    private boolean typescriptDryRun = false;

    /** Agent Worker 访问平台能力的唯一入口；默认直接调用本机 Chat，不依赖网关地址 */
    private String chatBaseUrl = "http://127.0.0.1:13103/chat";

    /** 数据格式校验器默认 content type */
    private String validateContentType = "json";

    /** 数据格式校验器默认结构语义模板 */
    private String validateStructure = "";

    /** 调用超时（毫秒） */
    private Long timeoutMs = 1000L * 60 * 30;
}
