package ai.platform.aiassit.model.entity.vo;

import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiModelManageVO extends AuditableDTO {

    private Long clientId;

    private String clientCode;

    private String clientName;

    private String modelCode;

    private String modelName;

    private AiChatClientType clientType;

    private String baseUrl;

    private String apiModel;

    private Boolean enabled;

    private String apiKeyMasked;

    private Map<String, Object> extJson;

}
