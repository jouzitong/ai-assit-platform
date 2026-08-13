package ai.platform.aiassit.service.ai.api.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 非流式 OpenAI Chat Completions 响应。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiChatCompletionResponse implements Serializable {

    private String id;

    /** 标准值为 {@code chat.completion}。 */
    private String object;

    /** Unix 秒级响应创建时间。 */
    private Long created;

    private String model;

    @JsonProperty("system_fingerprint")
    private String systemFingerprint;

    private List<OpenAiChatCompletionChoice> choices;

    private OpenAiUsage usage;
}
