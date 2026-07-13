package ai.platform.aiassit.data.virtualization.core.knowledge;

import lombok.Data;

@Data
public class VirtualDescriptionGenerateRequest {
    private Long entityId;
    private String currentDescription;
}
