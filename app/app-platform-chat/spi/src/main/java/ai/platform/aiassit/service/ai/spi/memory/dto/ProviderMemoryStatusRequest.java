package ai.platform.aiassit.service.ai.spi.memory.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProviderMemoryStatusRequest extends ProviderMemoryRequest {
    private String memoryId;
    private String messageId;
    private boolean enabled;
}
