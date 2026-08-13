package ai.platform.aiassit.service.ai.api.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/** OpenAI 用量统计对象，可用于 Chat Completions 和 Embeddings 响应。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiUsage implements Serializable {

    @JsonProperty("prompt_tokens")
    private Integer promptTokens;

    @JsonProperty("completion_tokens")
    private Integer completionTokens;

    @JsonProperty("total_tokens")
    private Integer totalTokens;

    @JsonProperty("prompt_tokens_details")
    private Map<String, Object> promptTokensDetails;

    @JsonProperty("completion_tokens_details")
    private Map<String, Object> completionTokensDetails;
}
