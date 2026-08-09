package ai.platform.aiassit.conversation.memory;

import ai.platform.aiassit.service.ai.api.memory.enums.MemoryProviderType;
import ai.platform.aiassit.service.ai.spi.memory.MemoryService;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ConversationMemoryProviderRegistry {

    private final Map<MemoryProviderType, MemoryService> providers = new EnumMap<>(MemoryProviderType.class);

    public ConversationMemoryProviderRegistry(List<MemoryService> services) {
        for (MemoryService service : services == null ? List.<MemoryService>of() : services) {
            MemoryService previous = providers.put(service.providerType(), service);
            if (previous != null) {
                throw new IllegalStateException("Duplicate Memory provider: " + service.providerType());
            }
        }
    }

    public MemoryService require(MemoryProviderType type) {
        MemoryService service = providers.get(type);
        if (service == null) {
            throw new IllegalStateException("Memory provider is not available: " + type);
        }
        return service;
    }
}
