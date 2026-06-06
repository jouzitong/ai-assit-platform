package ai.platform.aiassist.service.ai.meta.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiProviderConfigDTO extends BaseDTO {

    private String providerCode;

    private String providerName;

    private String baseUrl;

    private Integer connectTimeoutMs;

    private Integer readTimeoutMs;

    private Boolean enabled;

    private String remark;
}
