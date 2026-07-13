package ai.platform.aiassit.service.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiTextGenerationResponse implements Serializable {
    private String text;
    private String model;
    private String requestId;
}
