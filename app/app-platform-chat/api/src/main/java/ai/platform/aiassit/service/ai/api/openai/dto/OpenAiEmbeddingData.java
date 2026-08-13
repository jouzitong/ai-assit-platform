package ai.platform.aiassit.service.ai.api.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
/** OpenAI Embeddings 响应中的单条向量。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiEmbeddingData implements Serializable {

    /** 标准值为 {@code embedding}。 */
    private String object;

    private Integer index;

    /**
     * 向量正文：当 {@code encoding_format=float} 时为浮点数组；当请求选择
     * {@code base64} 时为 Base64 字符串。
     */
    private Object embedding;
}
