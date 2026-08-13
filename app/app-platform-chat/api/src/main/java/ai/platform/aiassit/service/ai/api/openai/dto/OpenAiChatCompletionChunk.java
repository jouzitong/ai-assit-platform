package ai.platform.aiassit.service.ai.api.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 流式 Chat Completions 中单个 SSE {@code data:} 事件的 JSON 数据。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiChatCompletionChunk implements Serializable {

    private String id;

    /** 标准值为 {@code chat.completion.chunk}。 */
    private String object;

    private Long created;

    private String model;

    @JsonProperty("system_fingerprint")
    private String systemFingerprint;

    private List<OpenAiChatCompletionChoice> choices;

    /** 仅当请求 {@code stream_options.include_usage=true} 时允许在末尾事件中出现。 */
    private OpenAiUsage usage;
}
