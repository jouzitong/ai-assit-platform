package ai.platform.aiassit.service.ai.api.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** OpenAI {@code GET /openai/v1/models} 响应。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiModelListResponse implements Serializable {

    /** 标准值为 {@code list}。 */
    private String object;

    /** 调用方当前可用的模型集合。 */
    private List<OpenAiModel> data;
}
