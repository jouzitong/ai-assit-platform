package ai.platform.aiassit.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiTextGenerationRequest implements Serializable {
    private String systemPrompt;
    private String userPrompt;
    private String scene;
    private Integer maxTokens;
    private Double temperature;
}
