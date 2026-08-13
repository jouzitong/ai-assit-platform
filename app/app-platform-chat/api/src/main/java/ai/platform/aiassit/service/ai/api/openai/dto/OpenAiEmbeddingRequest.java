package ai.platform.aiassit.service.ai.api.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/** OpenAI {@code POST /openai/v1/embeddings} 请求。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiEmbeddingRequest implements Serializable {

    /** 必填；值必须来自 {@code GET /openai/v1/models} 返回项的 {@code id}。 */
    private String model;

    /**
     * 必填；可为单个字符串、字符串数组、token ID 数组或 token ID 批次，保持 OpenAI 标准输入形态。
     */
    private Object input;

    @JsonProperty("encoding_format")
    private String encodingFormat;

    /** 目标向量维度；仅当所选模型支持缩维时有效。 */
    private Integer dimensions;

    private String user;
}
