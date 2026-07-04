package ai.platform.aiassit.service.ai.meta.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiModelManageDTO extends BaseDTO {

    private String modelCode;

    private String modelName;

    private String providerCode;

    private String providerName;

    private String baseUrl;

    private String apiModel;

    private Boolean enabled;

    private String apiKey;

    private Map<String, Object> extJson;
}
