package ai.platform.aiassit.service.ai.provider.service;

import ai.platform.aiassit.service.ai.api.memory.enums.MemoryProviderType;
import ai.platform.aiassit.service.ai.provider.client.RagflowMemoryClient;
import ai.platform.aiassit.service.ai.spi.memory.MemoryService;
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
import org.springframework.stereotype.Service;

/** RAGFlow adapter for the independent Memory SPI. */
@Service
public class RagflowMemoryProvider implements MemoryService {

    private final RagflowMemoryClient client;

    public RagflowMemoryProvider(RagflowMemoryClient client) {
        this.client = client;
    }

    @Override
    public MemoryProviderType providerType() {
        return MemoryProviderType.RAGFLOW;
    }

    @Override
    public MemoryDescriptor createMemory(ProviderMemoryCreateRequest request) {
        return client.create(request);
    }

    @Override
    public MemoryDescriptor getMemory(ProviderMemoryGetRequest request) {
        return client.get(request);
    }

    @Override
    public MemoryDescriptor updateMemory(ProviderMemoryUpdateRequest request) {
        return client.update(request);
    }

    @Override
    public void deleteMemory(ProviderMemoryDeleteRequest request) {
        client.delete(request);
    }

    @Override
    public MemoryWriteResponse addConversation(ProviderMemoryWriteRequest request) {
        return client.addConversation(request);
    }

    @Override
    public MemoryPageResponse listMessages(ProviderMemoryListRequest request) {
        return client.list(request);
    }

    @Override
    public MemorySearchResponse searchMessages(ProviderMemorySearchRequest request) {
        return client.search(request);
    }

    @Override
    public MemoryRecentResponse recentMessages(ProviderMemoryRecentRequest request) {
        return client.recent(request);
    }

    @Override
    public void updateMessageStatus(ProviderMemoryStatusRequest request) {
        client.updateStatus(request);
    }

    @Override
    public void forgetMessage(ProviderMemoryForgetRequest request) {
        client.forget(request);
    }
}
