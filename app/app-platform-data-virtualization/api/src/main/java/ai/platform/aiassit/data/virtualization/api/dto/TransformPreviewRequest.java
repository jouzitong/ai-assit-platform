package ai.platform.aiassit.data.virtualization.api.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class TransformPreviewRequest {
    private Long ruleId;
    private Boolean writeDirection = false;
    private Map<String, Object> inputs = new LinkedHashMap<>();
}
