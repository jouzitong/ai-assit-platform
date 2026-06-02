package ai.platform.aiassit.chat.api.dto;

import lombok.Data;

@Data
public class AiChatMetaQueryRequest {

    private String providerCode;

    private String modelCode;

    private Boolean enabled;
}
