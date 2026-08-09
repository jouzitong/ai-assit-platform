package ai.platform.aiassit.service.ai.spi.memory.dto;

import ai.platform.aiassit.service.ai.api.memory.enums.MemoryType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProviderMemoryCreateRequest extends ProviderMemoryRequest {
    private String name;
    private List<MemoryType> memoryTypes = new ArrayList<>();
    private String embeddingModel;
    private String extractionModel;
    private String permission = "me";
    private Integer memorySize;
    private String forgettingPolicy = "FIFO";
}
