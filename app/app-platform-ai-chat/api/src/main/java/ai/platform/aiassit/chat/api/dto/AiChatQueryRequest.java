package ai.platform.aiassit.chat.api.dto;

import ai.platform.aiassit.chat.history.entity.enums.AiChatBusinessType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

import java.util.HashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatQueryRequest extends BaseRequest {

    private String sessionCode;

    private String sessionName;

    private Long userId;

    private AiChatBusinessType businessType;

    private String providerCode;

    private String modelCode;

    private String prompt;

    private Double temperature;

    private Double topP;

    private Integer maxTokens;

    private Integer timeoutMs;

    private String traceId;

    private String scene;

    private Map<String, Object> ext = new HashMap<>();
}
