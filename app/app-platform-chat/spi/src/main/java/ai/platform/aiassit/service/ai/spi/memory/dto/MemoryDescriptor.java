package ai.platform.aiassit.service.ai.spi.memory.dto;

import ai.platform.aiassit.service.ai.api.memory.enums.MemoryProviderType;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryType;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Provider resource metadata only; prompts and credentials are intentionally omitted. */
@Data
public class MemoryDescriptor implements Serializable {
    private MemoryProviderType providerType;
    private String memoryId;
    private String name;
    private List<MemoryType> memoryTypes = new ArrayList<>();
    private String embeddingModel;
    private String extractionModel;
    private String permission;
    private Integer memorySize;
    private String forgettingPolicy;
}
