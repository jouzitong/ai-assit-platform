package ai.platform.aiassit.service.ai.api.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;

/** OpenAI 兼容错误的内部 error 对象。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiError implements Serializable {

    private String message;

    private String type;

    private String param;

    private String code;
}
