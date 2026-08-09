package ai.platform.aiassit.service.ai.spi.memory.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProviderMemoryUpdateRequest extends ProviderMemoryRequest {
    private String memoryId;
    private String name;
    private String extractionModel;
    private String permission;
    private Integer memorySize;
    private String forgettingPolicy;
    private Double temperature;
}
