package ai.platform.aiassit.service.ai.spi.memory.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProviderMemoryGetRequest extends ProviderMemoryRequest {
    private String memoryId;
}
