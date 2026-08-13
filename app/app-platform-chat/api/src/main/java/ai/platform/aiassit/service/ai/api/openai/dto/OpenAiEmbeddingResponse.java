package ai.platform.aiassit.service.ai.api.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** OpenAI {@code POST /openai/v1/embeddings} 响应。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiEmbeddingResponse implements Serializable {

    /** 标准值为 {@code list}。 */
    private String object;

    private List<OpenAiEmbeddingData> data;

    private String model;

    private OpenAiUsage usage;
}
