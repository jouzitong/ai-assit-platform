package ai.platform.aiassit.chat.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiModelCredentialDto {

    private Long id;

    private String credentialCode;

    private String providerCode;

    private String modelCode;

    private String apiKeyMasked;

    private Integer keyVersion;

    private Boolean enabled;

    private LocalDateTime expireAt;

    private String remark;
}
