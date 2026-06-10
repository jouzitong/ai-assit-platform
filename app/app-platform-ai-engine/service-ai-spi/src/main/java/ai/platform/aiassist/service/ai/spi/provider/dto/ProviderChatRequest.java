package ai.platform.aiassist.service.ai.spi.provider.dto;

import ai.platform.aiassist.service.ai.api.dto.ChatMessage;
import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import ai.platform.aiassist.service.ai.api.dto.ResponseFormat;
import ai.platform.aiassist.service.ai.api.dto.ToolDefinition;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 服务商聊天请求对象。
 *
 * <p>用于屏蔽不同模型服务商的请求差异，将平台内部统一的对话请求参数
 * 转换为具体 Provider 可识别的调用参数。</p>
 */
@Data
public class ProviderChatRequest {
    /**
     * 模型名称。
     *
     * <p>例如：gpt-4o、qwen-plus、deepseek-chat 等，具体值由模型配置决定。</p>
     */
    private String model;

    /**
     * 对话消息列表。
     *
     * <p>包含 system、user、assistant、tool 等角色消息，是模型生成回复的主要上下文。</p>
     */
    private List<ChatMessage> messages = new ArrayList<>();

    /**
     * 工具定义列表。
     *
     * <p>当模型需要调用函数、插件或外部工具时，通过该字段声明可用工具。</p>
     */
    private List<ToolDefinition> tools = new ArrayList<>();

    /**
     * 响应格式配置。
     *
     * <p>用于约束模型输出格式，例如普通文本、JSON 对象、JSON Schema 等。</p>
     */
    private ResponseFormat responseFormat;

    /**
     * 采样温度。
     *
     * <p>值越高，输出越随机；值越低，输出越稳定。不同 Provider 的取值范围可能不同。</p>
     */
    private Double temperature;

    /**
     * 核采样参数。
     *
     * <p>用于控制候选词概率分布范围，通常与 temperature 二选一或配合使用。</p>
     */
    private Double topP;

    /**
     * 最大输出 Token 数。
     *
     * <p>用于限制模型本次响应最多生成的 Token 数量。</p>
     */
    private Integer maxTokens;

    /**
     * 请求超时时间，单位：毫秒。
     *
     * <p>用于控制调用模型服务商接口时的最大等待时间。</p>
     */
    private Integer timeoutMs;

    /**
     * 请求元数据。
     *
     * <p>通常用于存放 traceId、userId、业务来源、租户等链路追踪或业务上下文信息。</p>
     */
    private RequestMeta meta;

    /**
     * 扩展参数。
     *
     * <p>用于承载不同 Provider 的特殊参数，避免频繁修改通用请求对象结构。</p>
     */
    private Map<String, Object> ext = new HashMap<>();
}
