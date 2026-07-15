package ai.platform.aiassit.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiTextGenerationRequest implements Serializable {
    /** 平台模型编码，对应 {@code ai_model_config.model_code}。 */
    private String modelCode;
    private String systemPrompt;
    private String userPrompt;
    private String scene;
    private Integer maxTokens;
    private Double temperature;
}
