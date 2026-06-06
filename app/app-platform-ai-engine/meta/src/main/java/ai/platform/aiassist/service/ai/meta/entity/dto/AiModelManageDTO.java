package ai.platform.aiassist.service.ai.meta.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiModelManageDTO extends BaseDTO {

    private String modelCode;

    private String modelName;

    private String providerCode;

    private String providerName;

    private String apiModel;

    private String capabilityTags;

    private Integer maxContextTokens;

    private Integer maxOutputTokens;

    private Integer temperatureEnabled;

    private Boolean enabled;

    private Integer priority;

    private String remark;

    private Long credentialId;

    private String credentialCode;

    private String apiKeyInput;

    private String apiKeyMasked;

    private Integer keyVersion;

    private Boolean credentialEnabled;

    private LocalDateTime expireAt;

    private String credentialRemark;
}
