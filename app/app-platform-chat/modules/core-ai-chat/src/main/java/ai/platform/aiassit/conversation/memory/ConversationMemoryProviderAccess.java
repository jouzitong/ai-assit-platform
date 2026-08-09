package ai.platform.aiassit.conversation.memory;

import ai.platform.aiassit.conversation.data.entity.ConversationMemoryBindingEntity;
import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryScope;
import ai.platform.aiassit.service.ai.spi.memory.MemoryService;
import ai.platform.aiassit.service.ai.spi.memory.MemoryProviderException;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryMessage;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryPageResponse;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryForgetRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryListRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryDeleteRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryStatusRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryWriteRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryWriteResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Centralizes binding and Provider-message ownership checks for every Memory read/write path. */
@Service
public class ConversationMemoryProviderAccess {

    private static final int LOOKUP_PAGE_SIZE = 100;
    private static final int MAX_LOOKUP_PAGES = 20;
    private static final int MAX_SESSION_DELETE_PASSES = 20;

    private final ConversationMemoryProvisionService provisionService;
    private final ConversationMemoryProviderRegistry providerRegistry;
    private final ConversationMemoryIdentity identity;
    private final ai.platform.aiassit.conversation.memory.config.ConversationMemoryProperties properties;

    public ConversationMemoryProviderAccess(ConversationMemoryProvisionService provisionService,
                                            ConversationMemoryProviderRegistry providerRegistry,
                                            ConversationMemoryIdentity identity,
                                            ai.platform.aiassit.conversation.memory.config.ConversationMemoryProperties properties) {
        this.provisionService = provisionService;
        this.providerRegistry = providerRegistry;
        this.identity = identity;
        this.properties = properties;
    }

    public List<MemoryMessage> listOwned(ConversationMemoryBindingEntity binding,
                                         String tenantId,
                                         Long userId,
                                         MemoryScope scope,
                                         String sessionCode,
                                         int page,
                                         int pageSize) {
        String memoryId = requireOwnedMemory(binding, tenantId, userId, scope);
        ProviderMemoryListRequest request = new ProviderMemoryListRequest();
        request.setMeta(provisionService.providerMeta(tenantId));
        request.setMemoryId(memoryId);
        request.setSessionId(scope == MemoryScope.SESSION ? requireText(sessionCode) : null);
        request.setUserId(identity.providerUserId(tenantId, userId));
        request.setPage(Math.max(1, page));
        request.setPageSize(Math.max(1, Math.min(pageSize, 200)));
        MemoryPageResponse response = provider().listMessages(request);
        List<MemoryMessage> items = response == null || response.getItems() == null
                ? List.of() : response.getItems();
        return validateItems(items, memoryId, identity.providerUserId(tenantId, userId), scope, sessionCode);
    }

    /**
     * Looks up a write by its opaque Provider idempotency locator and validates the complete
     * response against the current binding. An empty result is an explicit "not found" only when
     * the Provider accepted the query; transport/provider failures are propagated to the worker
     * so they remain UNKNOWN.
     */
    public MemoryMessage findOwnedByExternalId(ConversationMemoryBindingEntity binding,
                                               String tenantId,
                                               Long userId,
                                               MemoryScope scope,
                                               String sessionCode,
                                               String externalId) {
        String memoryId = requireOwnedMemory(binding, tenantId, userId, scope);
        String lookupId = requireText(externalId);
        ProviderMemoryListRequest request = new ProviderMemoryListRequest();
        request.setMeta(provisionService.providerMeta(tenantId));
        request.setMemoryId(memoryId);
        request.setExternalId(lookupId);
        request.setUserId(identity.providerUserId(tenantId, userId));
        request.setSessionId(scope == MemoryScope.SESSION ? requireText(sessionCode) : null);
        request.setPage(1);
        request.setPageSize(LOOKUP_PAGE_SIZE);
        MemoryPageResponse response = provider().listMessages(request);
        List<MemoryMessage> items = response == null || response.getItems() == null
                ? List.of() : response.getItems();
        List<MemoryMessage> validated = validateItems(
                items, memoryId, identity.providerUserId(tenantId, userId), scope, sessionCode);
        MemoryMessage found = null;
        for (MemoryMessage item : validated) {
            if (!lookupId.equals(item.getExternalId())) {
                continue;
            }
            if (found != null && !java.util.Objects.equals(found.getMessageId(), item.getMessageId())) {
                throw new MemoryProviderException("MEMORY_EXTERNAL_ID_AMBIGUOUS",
                        "Provider returned multiple messages for one external id", true, null);
            }
            found = item;
        }
        return found;
    }

    public MemoryMessage requireOwnedMessage(ConversationMemoryBindingEntity binding,
                                             String tenantId,
                                             Long userId,
                                             MemoryScope scope,
                                             String sessionCode,
                                             String messageId) {
        String expectedMessageId = requireText(messageId);
        for (int page = 1; page <= MAX_LOOKUP_PAGES; page++) {
            List<MemoryMessage> items = listOwned(
                    binding, tenantId, userId, scope, sessionCode, page, LOOKUP_PAGE_SIZE);
            for (MemoryMessage item : items) {
                if (item != null && expectedMessageId.equals(item.getMessageId())) {
                    return item;
                }
            }
            if (items.size() < LOOKUP_PAGE_SIZE) {
                break;
            }
        }
        throw new MemoryNotFoundException();
    }

    public MemoryWriteResponse addConversation(String tenantId, ProviderMemoryWriteRequest request) {
        request.setMeta(provisionService.providerMeta(tenantId));
        return provider().addConversation(request);
    }

    public void updateStatus(String tenantId, String memoryId, String messageId, boolean enabled) {
        ProviderMemoryStatusRequest request = new ProviderMemoryStatusRequest();
        request.setMeta(provisionService.providerMeta(tenantId));
        request.setMemoryId(memoryId);
        request.setMessageId(messageId);
        request.setEnabled(enabled);
        provider().updateMessageStatus(request);
    }

    public void forget(String tenantId, String memoryId, String messageId) {
        ProviderMemoryForgetRequest request = new ProviderMemoryForgetRequest();
        request.setMeta(provisionService.providerMeta(tenantId));
        request.setMemoryId(memoryId);
        request.setMessageId(messageId);
        provider().forgetMessage(request);
    }

    /**
     * Deletes a retired long-term Memory resource. The task is an internal, server-created
     * ownership proof: the binding must still point at the resource in its retiring slot. This
     * prevents a client-controlled Provider ID from reaching the delete endpoint.
     */
    public void deleteRetiredMemory(ConversationMemoryBindingEntity binding,
                                    String tenantId,
                                    Long userId,
                                    String memoryId) {
        if (binding == null || !"ACTIVE".equals(binding.getStatus())
                || !tenantId.equals(binding.getTenantId()) || !userId.equals(binding.getUserId())
                || !StringUtils.hasText(memoryId)
                || !memoryId.equals(binding.getRetiringLongTermMemoryId())) {
            throw new MemoryOwnershipViolationException();
        }
        ProviderMemoryDeleteRequest request = new ProviderMemoryDeleteRequest();
        request.setMeta(provisionService.providerMeta(tenantId));
        request.setMemoryId(memoryId);
        provider().deleteMemory(request);
    }

    /**
     * Deletes only messages belonging to one session from the shared SESSION Memory. Page one is
     * repeatedly re-read because deleting while walking forward pages can shift remaining records.
     */
    public void forgetOwnedSession(ConversationMemoryBindingEntity binding,
                                   String tenantId,
                                   Long userId,
                                   String sessionCode,
                                   String expectedMemoryId) {
        String memoryId = requireOwnedMemory(binding, tenantId, userId, MemoryScope.SESSION);
        if (StringUtils.hasText(expectedMemoryId) && !memoryId.equals(expectedMemoryId)) {
            throw new MemoryOwnershipViolationException();
        }
        Set<String> forgottenIds = new HashSet<>();
        for (int pass = 0; pass < MAX_SESSION_DELETE_PASSES; pass++) {
            List<MemoryMessage> items = listOwned(
                    binding, tenantId, userId, MemoryScope.SESSION, sessionCode, 1, LOOKUP_PAGE_SIZE);
            if (items.isEmpty()) {
                return;
            }
            int newItems = 0;
            for (MemoryMessage item : items) {
                if (item == null || !StringUtils.hasText(item.getMessageId())
                        || !forgottenIds.add(item.getMessageId())) {
                    continue;
                }
                forget(tenantId, memoryId, item.getMessageId());
                newItems++;
            }
            if (newItems == 0) {
                throw new MemoryProviderException("MEMORY_SESSION_DELETE_STALLED",
                        "Memory session cleanup did not make progress", true, null);
            }
        }
        throw new MemoryProviderException("MEMORY_SESSION_DELETE_INCOMPLETE",
                "Memory session cleanup exceeded the safety limit", true, null);
    }

    public String requireOwnedMemory(ConversationMemoryBindingEntity binding,
                                     String tenantId,
                                     Long userId,
                                     MemoryScope scope) {
        if (binding == null || !"ACTIVE".equals(binding.getStatus())
                || !tenantId.equals(binding.getTenantId()) || !userId.equals(binding.getUserId())) {
            throw new MemoryOwnershipViolationException();
        }
        String memoryId = scope == MemoryScope.LONG_TERM
                ? binding.getLongTermMemoryId() : binding.getSessionMemoryId();
        if (!StringUtils.hasText(memoryId)) {
            throw new MemoryOwnershipViolationException();
        }
        return memoryId;
    }

    public String providerUserId(String tenantId, Long userId) {
        return identity.providerUserId(tenantId, userId);
    }

    public RequestMeta providerMeta(String tenantId) {
        return provisionService.providerMeta(tenantId);
    }

    private List<MemoryMessage> validateItems(List<MemoryMessage> source,
                                              String expectedMemoryId,
                                              String expectedUserId,
                                              MemoryScope scope,
                                              String expectedSessionId) {
        List<MemoryMessage> validated = new ArrayList<>();
        for (MemoryMessage item : source) {
            if (item == null) {
                continue;
            }
            if (StringUtils.hasText(item.getMemoryId()) && !expectedMemoryId.equals(item.getMemoryId())) {
                throw new MemoryOwnershipViolationException();
            }
            if (!StringUtils.hasText(item.getUserId()) || !expectedUserId.equals(item.getUserId())) {
                throw new MemoryOwnershipViolationException();
            }
            if (scope == MemoryScope.SESSION
                    && (!StringUtils.hasText(item.getSessionId()) || !expectedSessionId.equals(item.getSessionId()))) {
                throw new MemoryOwnershipViolationException();
            }
            if (!StringUtils.hasText(item.getMemoryId())) {
                item.setMemoryId(expectedMemoryId);
            }
            validated.add(item);
        }
        return validated;
    }

    private MemoryService provider() {
        return providerRegistry.require(properties.getProviderType());
    }

    private String requireText(String value) {
        if (!StringUtils.hasText(value)) {
            throw new MemoryNotFoundException();
        }
        return value.trim();
    }

    public static class MemoryOwnershipViolationException extends RuntimeException {
    }

    public static class MemoryNotFoundException extends RuntimeException {
    }
}
