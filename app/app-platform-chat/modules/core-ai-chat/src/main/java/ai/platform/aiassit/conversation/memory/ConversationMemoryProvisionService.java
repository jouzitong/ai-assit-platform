package ai.platform.aiassit.conversation.memory;

import ai.platform.aiassit.conversation.data.entity.ConversationMemoryBindingEntity;
import ai.platform.aiassit.conversation.data.service.ConversationMemoryBindingDataService;
import ai.platform.aiassit.conversation.memory.config.ConversationMemoryProperties;
import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryProviderType;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryScope;
import ai.platform.aiassit.service.ai.spi.config.ProviderClientConfigurationResolver;
import ai.platform.aiassit.service.ai.spi.memory.MemoryService;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryDescriptor;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryCreateRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryGetRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryDeleteRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Lazily provisions the two external Memory resources while persisting only ownership metadata. */
@Service
public class ConversationMemoryProvisionService {

    private final ConversationMemoryBindingDataService bindingDataService;
    private final ConversationMemoryProviderRegistry providerRegistry;
    private final ProviderClientConfigurationResolver clientConfigurationResolver;
    private final ConversationMemoryIdentity identity;
    private final ConversationMemoryProperties properties;
    private final String provisionOwner = UUID.randomUUID().toString().replace("-", "");

    public ConversationMemoryProvisionService(ConversationMemoryBindingDataService bindingDataService,
                                              ConversationMemoryProviderRegistry providerRegistry,
                                              ProviderClientConfigurationResolver clientConfigurationResolver,
                                              ConversationMemoryIdentity identity,
                                              ConversationMemoryProperties properties) {
        this.bindingDataService = bindingDataService;
        this.providerRegistry = providerRegistry;
        this.clientConfigurationResolver = clientConfigurationResolver;
        this.identity = identity;
        this.properties = properties;
    }

    public ConversationMemoryBindingEntity findActive(String tenantId, Long userId) {
        if (!properties.isEnabled()) {
            return null;
        }
        ConversationMemoryBindingEntity binding = bindingDataService.find(
                tenantId, userId, properties.getProviderType().name(), properties.getClientKey());
        return usable(binding) ? binding : null;
    }

    /**
     * Marks the current long-term resource as retiring and reserves the binding for a pointer
     * rotation. The old Provider ID is control metadata only; its content remains in RAGFlow.
     */
    public ConversationMemoryBindingEntity beginLongTermMigration(String tenantId, Long userId) {
        validateConfiguration();
        ConversationMemoryBindingEntity binding = bindingDataService.find(
                tenantId, userId, properties.getProviderType().name(), properties.getClientKey());
        if (!usable(binding) || !properties.isLongTermEnabled()
                || !StringUtils.hasText(binding.getLongTermMemoryId())) {
            throw new IllegalStateException("Long-term Memory binding is unavailable");
        }
        if (StringUtils.hasText(binding.getRetiringLongTermMemoryId())) {
            throw new IllegalStateException("Long-term Memory migration is already in progress");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!bindingDataService.acquireLongTermMigrationLease(
                binding.getId(), binding.getLongTermMemoryId(), provisionOwner, now,
                now.plus(properties.getProvision().getLeaseTimeout()))) {
            throw new IllegalStateException("Long-term Memory migration is already in progress");
        }
        return bindingDataService.find(
                tenantId, userId, properties.getProviderType().name(), properties.getClientKey());
    }

    /** Creates a new Provider Memory using only server-side configuration. */
    public MemoryDescriptor createMemory(String tenantId, Long userId, MemoryScope scope) {
        validateConfiguration();
        MemoryService provider = providerRegistry.require(properties.getProviderType());
        return provider.createMemory(createRequest(scope, tenantId, userId, providerMeta(tenantId)));
    }

    public boolean completeLongTermMigration(String tenantId, Long userId,
                                             String oldMemoryId, String newMemoryId) {
        ConversationMemoryBindingEntity binding = bindingDataService.find(
                tenantId, userId, properties.getProviderType().name(), properties.getClientKey());
        return binding != null && bindingDataService.switchLongTermMemory(
                binding.getId(), oldMemoryId, newMemoryId, provisionOwner, LocalDateTime.now());
    }

    public boolean abortLongTermMigration(String tenantId, Long userId, String oldMemoryId) {
        ConversationMemoryBindingEntity binding = bindingDataService.find(
                tenantId, userId, properties.getProviderType().name(), properties.getClientKey());
        return binding != null && bindingDataService.abortLongTermMigration(
                binding.getId(), oldMemoryId, provisionOwner);
    }

    public boolean clearRetiringLongTermMemory(ConversationMemoryBindingEntity binding, String oldMemoryId) {
        return binding != null && bindingDataService.clearRetiringLongTermMemory(binding.getId(), oldMemoryId);
    }

    /** Best-effort cleanup for a newly created resource when a local pointer rotation aborts. */
    public void deleteProvisionedMemory(String tenantId, String memoryId) {
        if (!StringUtils.hasText(memoryId)) {
            return;
        }
        ProviderMemoryDeleteRequest request = new ProviderMemoryDeleteRequest();
        request.setMeta(providerMeta(tenantId));
        request.setMemoryId(memoryId);
        providerRegistry.require(properties.getProviderType()).deleteMemory(request);
    }

    public ConversationMemoryBindingEntity ensureBinding(String tenantId, Long userId) {
        validateConfiguration();
        ConversationMemoryBindingEntity binding = bindingDataService.find(
                tenantId, userId, properties.getProviderType().name(), properties.getClientKey());
        if (usable(binding)) {
            verifyIfDue(binding);
            return binding;
        }
        if (binding == null) {
            ConversationMemoryBindingEntity candidate = new ConversationMemoryBindingEntity();
            candidate.setBindingCode("memory-binding-" + UUID.randomUUID().toString().replace("-", ""));
            candidate.setTenantId(tenantId);
            candidate.setUserId(userId);
            candidate.setProviderType(properties.getProviderType().name());
            candidate.setClientKey(properties.getClientKey());
            candidate.setSchemaVersion(properties.getSchemaVersion());
            candidate.setStatus("CREATING");
            binding = bindingDataService.insertIfAbsent(candidate);
        }
        if (binding.getSchemaVersion() == null || binding.getSchemaVersion() != properties.getSchemaVersion()) {
            throw new IllegalStateException("Memory binding schema migration is required");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseUntil = now.plus(properties.getProvision().getLeaseTimeout());
        if (!bindingDataService.acquireProvisionLease(binding.getId(), provisionOwner, now, leaseUntil)) {
            throw new IllegalStateException("Memory binding provisioning is already in progress");
        }
        binding = bindingDataService.find(tenantId, userId,
                properties.getProviderType().name(), properties.getClientKey());
        return provision(binding);
    }

    private ConversationMemoryBindingEntity provision(ConversationMemoryBindingEntity binding) {
        try {
            MemoryService provider = providerRegistry.require(properties.getProviderType());
            RequestMeta meta = providerMeta(binding.getTenantId());
            if (properties.isSessionEnabled() && !StringUtils.hasText(binding.getSessionMemoryId())) {
                MemoryDescriptor session = provider.createMemory(createRequest(
                        MemoryScope.SESSION, binding.getTenantId(), binding.getUserId(), meta));
                binding.setSessionMemoryId(session.getMemoryId());
                bindingDataService.update(binding);
            }
            if (properties.isLongTermEnabled() && !StringUtils.hasText(binding.getLongTermMemoryId())) {
                MemoryDescriptor longTerm = provider.createMemory(createRequest(
                        MemoryScope.LONG_TERM, binding.getTenantId(), binding.getUserId(), meta));
                binding.setLongTermMemoryId(longTerm.getMemoryId());
                bindingDataService.update(binding);
            }
            if (!usableIds(binding)) {
                throw new IllegalStateException("Memory provider did not return all required resource IDs");
            }
            binding.setStatus("ACTIVE");
            binding.setLastVerifiedAt(LocalDateTime.now());
            binding.setProvisionOwner(null);
            binding.setProvisionLeaseUntil(null);
            bindingDataService.update(binding);
            return binding;
        } catch (RuntimeException ex) {
            binding.setStatus("FAILED");
            binding.setProvisionOwner(null);
            binding.setProvisionLeaseUntil(null);
            bindingDataService.update(binding);
            throw ex;
        }
    }

    private void verifyIfDue(ConversationMemoryBindingEntity binding) {
        LocalDateTime lastVerified = binding.getLastVerifiedAt();
        if (lastVerified != null && lastVerified.plus(properties.getProvision().getVerifyTtl())
                .isAfter(LocalDateTime.now())) {
            return;
        }
        MemoryService provider = providerRegistry.require(properties.getProviderType());
        RequestMeta meta = providerMeta(binding.getTenantId());
        if (properties.isSessionEnabled()) {
            provider.getMemory(getRequest(binding.getSessionMemoryId(), meta));
        }
        if (properties.isLongTermEnabled()) {
            provider.getMemory(getRequest(binding.getLongTermMemoryId(), meta));
        }
        binding.setLastVerifiedAt(LocalDateTime.now());
        bindingDataService.update(binding);
    }

    private ProviderMemoryCreateRequest createRequest(MemoryScope scope,
                                                      String tenantId,
                                                      Long userId,
                                                      RequestMeta meta) {
        ProviderMemoryCreateRequest request = new ProviderMemoryCreateRequest();
        request.setMeta(meta);
        request.setName(identity.memoryName(scope, tenantId, userId));
        request.setMemoryTypes(scope == MemoryScope.LONG_TERM
                ? properties.getLongTermMemoryTypes() : properties.getSessionMemoryTypes());
        request.setEmbeddingModel(properties.getEmbeddingModel());
        request.setExtractionModel(properties.getExtractionModel());
        request.setPermission("me");
        request.setMemorySize(properties.getMemorySize());
        request.setForgettingPolicy(properties.getForgettingPolicy());
        return request;
    }

    private ProviderMemoryGetRequest getRequest(String memoryId, RequestMeta meta) {
        ProviderMemoryGetRequest request = new ProviderMemoryGetRequest();
        request.setMemoryId(memoryId);
        request.setMeta(meta);
        return request;
    }

    public RequestMeta providerMeta(String tenantId) {
        RequestMeta meta = new RequestMeta();
        meta.setTenantId(tenantId);
        meta.setScene("chat-memory");
        meta.setExt(new LinkedHashMap<>());
        return clientConfigurationResolver.apply(properties.getClientKey(), AiKnowledgeClientType.RAGFLOW, meta);
    }

    private boolean usable(ConversationMemoryBindingEntity binding) {
        return binding != null && "ACTIVE".equals(binding.getStatus())
                && binding.getSchemaVersion() != null
                && binding.getSchemaVersion() == properties.getSchemaVersion()
                && usableIds(binding);
    }

    private boolean usableIds(ConversationMemoryBindingEntity binding) {
        return (!properties.isSessionEnabled() || StringUtils.hasText(binding.getSessionMemoryId()))
                && (!properties.isLongTermEnabled() || StringUtils.hasText(binding.getLongTermMemoryId()));
    }

    private void validateConfiguration() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Chat Memory is disabled");
        }
        if (properties.getProviderType() != MemoryProviderType.RAGFLOW) {
            throw new IllegalStateException("Unsupported Chat Memory provider: " + properties.getProviderType());
        }
        if (!properties.isSessionEnabled() && !properties.isLongTermEnabled()) {
            throw new IllegalStateException("At least one Chat Memory scope must be enabled");
        }
        if (!StringUtils.hasText(properties.getClientKey())
                || !StringUtils.hasText(properties.getEmbeddingModel())
                || !StringUtils.hasText(properties.getExtractionModel())
                || !StringUtils.hasText(properties.getIdentitySalt())) {
            throw new IllegalStateException("Chat Memory client, models and identity salt must be configured");
        }
    }
}
