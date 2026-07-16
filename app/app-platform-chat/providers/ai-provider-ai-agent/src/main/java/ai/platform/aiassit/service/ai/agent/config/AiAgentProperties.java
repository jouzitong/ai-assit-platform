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

    /** Python tool 调用知识库搜索接口的完整地址 */
    private String knowledgeSearchUrl = "http://127.0.0.1:9764/chat/api/v1/ai/execution/kb/search";

    /** Python/TypeScript 动态 Tool 调用 Java Tool Gateway 的服务基址 */
    private String toolGatewayUrl = "http://127.0.0.1:9764/chat";

    /** Python/TypeScript 按需读取已发布 Skill Resource 的 Java Gateway 服务基址 */
    private String skillGatewayUrl = "http://127.0.0.1:9764/chat";

    /** 数据格式校验器默认 content type */
    private String validateContentType = "json";

    /** 数据格式校验器默认结构语义模板 */
    private String validateStructure = "";

    /** 调用超时（毫秒） */
    private Integer timeoutMs = 60000;
}
