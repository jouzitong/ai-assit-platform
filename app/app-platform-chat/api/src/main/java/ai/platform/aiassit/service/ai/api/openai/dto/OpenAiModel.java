package ai.platform.aiassit.service.ai.api.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/** OpenAI {@code /openai/v1/models} 的单个模型对象。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiModel implements Serializable {

    /** 调用模型任务时使用的稳定模型标识。 */
    private String id;

    /** 标准值为 {@code model}。 */
    private String object;

    /** Unix 秒级创建时间；上游未提供时允许省略。 */
    private Long created;

    /** 模型所属方，对应 OpenAI 标准字段 {@code owned_by}。 */
    @JsonProperty("owned_by")
    private String ownedBy;
}
