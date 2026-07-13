package ai.platform.aiassit.model.entity.dto;

import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiClientConfigDTO extends AuditableDTO {
    private String clientCode;
    private String clientName;
    private AiChatClientType clientType;
    private String baseUrl;
    private String apiKey;
    private Boolean enabled;
    private Map<String, Object> extJson;
}
