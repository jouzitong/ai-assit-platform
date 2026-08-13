package ai.platform.aiassit.service.ai.api.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/** OpenAI {@code POST /openai/v1/chat/completions} 请求。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiChatCompletionRequest implements Serializable {

    /** 必填；值必须来自 {@code GET /openai/v1/models} 返回项的 {@code id}。 */
    private String model;

    /** 必填；按顺序发送给模型的上下文消息。 */
    private List<OpenAiChatMessage> messages;

    /** 是否以 Server-Sent Events 方式返回增量结果。 */
    private Boolean stream;

    /** 流式返回的附加选项，例如 {@code include_usage}。 */
    @JsonProperty("stream_options")
    private Map<String, Object> streamOptions;

    private Double temperature;

    @JsonProperty("top_p")
    private Double topP;

    /** 兼容当前 OpenAI Chat Completions 的 {@code max_tokens} 参数。 */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /** 兼容新模型使用的 {@code max_completion_tokens} 参数。 */
    @JsonProperty("max_completion_tokens")
    private Integer maxCompletionTokens;

    private Integer n;

    /** 可为单个停止词或停止词数组。 */
    private Object stop;

    @JsonProperty("presence_penalty")
    private Double presencePenalty;

    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;

    @JsonProperty("response_format")
    private Map<String, Object> responseFormat;

    /** OpenAI function/tool 定义集合。 */
    private List<Map<String, Object>> tools;

    /** 可为 {@code auto}、{@code none}、{@code required} 或指定工具对象。 */
    @JsonProperty("tool_choice")
    private Object toolChoice;

    @JsonProperty("parallel_tool_calls")
    private Boolean parallelToolCalls;

    /** 调用方的终端用户标识；实现方可用于限流和审计，不得回传。 */
    private String user;

    private Integer seed;
}
