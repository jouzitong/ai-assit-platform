package ai.platform.aiassit.service.ai.spi.memory;

import ai.platform.aiassit.service.ai.api.memory.enums.MemoryProviderType;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryDescriptor;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryPageResponse;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryRecentResponse;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemorySearchResponse;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryWriteResponse;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryCreateRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryDeleteRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryForgetRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryGetRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryListRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryRecentRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemorySearchRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryStatusRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryUpdateRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryWriteRequest;

/** Provider-neutral operations for externally owned conversation memory. */
public interface MemoryService {

    MemoryProviderType providerType();

    MemoryDescriptor createMemory(ProviderMemoryCreateRequest request);

    MemoryDescriptor getMemory(ProviderMemoryGetRequest request);

    MemoryDescriptor updateMemory(ProviderMemoryUpdateRequest request);

    void deleteMemory(ProviderMemoryDeleteRequest request);

    MemoryWriteResponse addConversation(ProviderMemoryWriteRequest request);

    MemoryPageResponse listMessages(ProviderMemoryListRequest request);

    MemorySearchResponse searchMessages(ProviderMemorySearchRequest request);

    MemoryRecentResponse recentMessages(ProviderMemoryRecentRequest request);

    void updateMessageStatus(ProviderMemoryStatusRequest request);

    void forgetMessage(ProviderMemoryForgetRequest request);
}
