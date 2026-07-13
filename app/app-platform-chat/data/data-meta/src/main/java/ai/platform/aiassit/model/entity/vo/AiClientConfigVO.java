package ai.platform.aiassit.model.entity.vo;

import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiClientConfigVO extends AuditableDTO {
    private String clientCode;
    private String clientName;
    private AiChatClientType clientType;
    private String baseUrl;
    private String apiKeyMasked;
    private Boolean enabled;
    private Integer modelCount;
    private Map<String, Object> extJson;
}
