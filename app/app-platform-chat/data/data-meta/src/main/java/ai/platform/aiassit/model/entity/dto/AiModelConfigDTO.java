package ai.platform.aiassit.model.entity.dto;

import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiModelConfigDTO extends BaseDTO {

    private Long clientId;

    private String modelCode;

    private String modelName;

    private AiChatClientType clientType;

    private String baseUrl;

    private String apiModel;

    private Boolean enabled;

    private String apiKey;

    private Map<String, Object> extJson;
}
