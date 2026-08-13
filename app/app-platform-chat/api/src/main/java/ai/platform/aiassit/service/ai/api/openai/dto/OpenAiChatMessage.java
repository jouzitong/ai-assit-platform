package ai.platform.aiassit.service.ai.api.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 消息对象。
 *
 * <p>{@code content} 保持为 {@link Object}，以兼容纯文本和 OpenAI 多模态内容分段数组；
 * {@code tool_calls} 同样保留原始对象，避免 API 契约在工具调用字段演进时丢失数据。</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiChatMessage implements Serializable {

    /** 标准角色，例如 {@code system}、{@code developer}、{@code user}、{@code assistant} 或 {@code tool}。 */
    private String role;

    /** 文本内容或 OpenAI 内容分段数组。 */
    private Object content;

    /** 可选的消息发送方名称。 */
    private String name;

    /** tool 角色消息对应的工具调用标识。 */
    @JsonProperty("tool_call_id")
    private String toolCallId;

    /** assistant 消息发起的 OpenAI 工具调用集合。 */
    @JsonProperty("tool_calls")
    private List<Map<String, Object>> toolCalls;
}
