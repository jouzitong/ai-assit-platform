package ai.platform.aiassist.service.ai.meta.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiModelCredentialDTO extends BaseDTO {

    private String credentialCode;

    private String providerCode;

    private String modelCode;

    private String apiKeyCiphertext;

    private String apiKeyMasked;

    private Integer keyVersion;

    private Boolean enabled;

    private LocalDateTime expireAt;

    private String remark;
}
