package ai.platform.aiassit.service.ai.api.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/** Chat Completions 的非流式 message 或流式 delta 结果项。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiChatCompletionChoice implements Serializable {

    private Integer index;

    /** 非流式响应中的完整 assistant 消息。 */
    private OpenAiChatMessage message;

    /** 流式响应中的增量消息片段。 */
    private OpenAiChatMessage delta;

    @JsonProperty("finish_reason")
    private String finishReason;

    /** 供应方支持时透传 OpenAI logprobs 结果。 */
    private Map<String, Object> logprobs;
}
