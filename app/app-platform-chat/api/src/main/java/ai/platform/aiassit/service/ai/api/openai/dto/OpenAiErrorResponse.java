package ai.platform.aiassit.service.ai.api.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;

/** OpenAI 兼容接口的统一失败响应：{@code {"error": {...}}}。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiErrorResponse implements Serializable {

    private OpenAiError error;
}
