package ai.platform.aiassit.service.ai.meta.entity.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiModelManageVO extends BaseDTO {

    private String modelCode;

    private String modelName;

    private String providerCode;

    private String providerName;

    private String baseUrl;

    private String apiModel;

    private Boolean enabled;

    private String apiKeyMasked;

    private Map<String, Object> extJson;
}
