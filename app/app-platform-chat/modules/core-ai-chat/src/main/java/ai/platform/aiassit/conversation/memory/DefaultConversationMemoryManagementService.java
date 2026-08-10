package ai.platform.aiassit.conversation.memory;

import ai.platform.aiassit.conversation.data.entity.ConversationMemoryBindingEntity;
import ai.platform.aiassit.conversation.data.entity.ConversationMemorySyncTaskEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import ai.platform.aiassit.conversation.data.entity.req.ConversationHistoryQueryRequest;
import ai.platform.aiassit.conversation.data.service.ConversationMemorySyncTaskDataService;
import ai.platform.aiassit.conversation.data.service.ConversationSessionService;
import ai.platform.aiassit.conversation.memory.ConversationMemoryProviderAccess.MemoryNotFoundException;
import ai.platform.aiassit.conversation.memory.ConversationMemoryProviderAccess.MemoryOwnershipViolationException;
import ai.platform.aiassit.conversation.memory.MemoryReferenceCodec.MemoryReference;
import ai.platform.aiassit.conversation.memory.config.ConversationMemoryProperties;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryConfirmRequest;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryContextResponse;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryCorrectionRequest;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryCreateRequest;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryCounts;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryItem;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryListResponse;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryOperationResponse;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemorySessionPolicyRequest;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryItemStatus;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryScope;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryType;
import ai.platform.aiassit.service.ai.spi.memory.MemoryProviderException;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryMessage;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryDescriptor;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryWriteResponse;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryWriteRequest;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * User-facing Memory orchestration. Memory text is read from or written to RAGFlow in-call and
 * is never persisted in the Java control plane.
 */
@Slf4j
@Service
public class DefaultConversationMemoryManagementService implements ConversationMemoryManagementService {

    private static final int CONTEXT_PAGE_SIZE = 100;
    private static final int MAX_VISIBLE_CONTENT_CODEPOINTS = 2_000;
    private static final String PROVIDER_AVAILABLE = "AVAILABLE";
    private static final String PROVIDER_UNAVAILABLE = "UNAVAILABLE";
    private static final String PROVIDER_DISABLED = "DISABLED";
    private static final String PROVIDER_BINDING_UNAVAILABLE = "BINDING_UNAVAILABLE";
    private static final String PROVIDER_SECURITY_REJECTED = "SECURITY_REJECTED";

    private final ConversationMemoryProvisionService provisionService;
    private final ConversationMemoryProviderAccess providerAccess;
    private final ConversationMemorySyncTaskDataService taskDataService;
    private final ConversationSessionService sessionService;
    private final MemorySessionPolicyService policyService;
    private final ConversationMemoryBridge memoryBridge;
    private final MemoryReferenceCodec referenceCodec;
    private final ConversationMemoryProperties properties;

    public DefaultConversationMemoryManagementService(ConversationMemoryProvisionService provisionService,
                                                      ConversationMemoryProviderAccess providerAccess,
                                                      ConversationMemorySyncTaskDataService taskDataService,
                                                      ConversationSessionService sessionService,
                                                      MemorySessionPolicyService policyService,
                                                      ConversationMemoryBridge memoryBridge,
                                                      MemoryReferenceCodec referenceCodec,
                                                      ConversationMemoryProperties properties) {
        this.provisionService = provisionService;
        this.providerAccess = providerAccess;
        this.taskDataService = taskDataService;
        this.sessionService = sessionService;
        this.policyService = policyService;
        this.memoryBridge = memoryBridge;
        this.referenceCodec = referenceCodec;
        this.properties = properties;
    }

    @Override
    public ConversationMemoryContextResponse context(String tenantId, Long userId, String sessionCode) {
        ownedSession(userId, sessionCode);
        ConversationMemoryContextResponse response = new ConversationMemoryContextResponse();
        response.setSessionCode(sessionCode);
        response.setGeneratedAt(Instant.now());
        List<ConversationMemoryItem> processing = processingItems(
                tenantId, userId, sessionCode, null, 50);
        response.setProcessingMemories(processing);
        response.setMemoryLag(!processing.isEmpty());
        if (!properties.isEnabled()) {
            response.setProviderStatus(PROVIDER_DISABLED);
            updateCounts(response);
            return response;
        }
        ConversationMemoryBindingEntity binding = provisionService.findActive(tenantId, userId);
        if (binding == null) {
            response.setProviderStatus(PROVIDER_BINDING_UNAVAILABLE);
            updateCounts(response);
            return response;
        }
        try {
            Set<String> excluded = policyService.excludedMessageKeys(tenantId, userId, sessionCode);
            List<ConversationMemoryItem> sessionItems = mapProviderItems(
                    providerAccess.listOwned(binding, tenantId, userId, MemoryScope.SESSION,
                            sessionCode, 1, CONTEXT_PAGE_SIZE),
                    tenantId, userId, MemoryScope.SESSION, excluded);
            List<ConversationMemoryItem> longTermItems = mapProviderItems(
                    providerAccess.listOwned(binding, tenantId, userId, MemoryScope.LONG_TERM,
                            null, 1, CONTEXT_PAGE_SIZE),
                    tenantId, userId, MemoryScope.LONG_TERM, excluded);
            distribute(sessionItems, response.getSessionMemories(), response.getProcessingMemories(),
                    response.getDisabledMemories());
            distribute(longTermItems, response.getLongTermMemories(), response.getProcessingMemories(),
                    response.getDisabledMemories());
            response.setProviderStatus(PROVIDER_AVAILABLE);
        } catch (MemoryOwnershipViolationException ex) {
            response.getSessionMemories().clear();
            response.getLongTermMemories().clear();
            response.getDisabledMemories().clear();
            response.setProviderStatus(PROVIDER_SECURITY_REJECTED);
            log.warn("Memory上下文归属校验失败，sessionCode={}，Provider结果已全部丢弃", sessionCode);
        } catch (RuntimeException ex) {
            response.setProviderStatus(PROVIDER_UNAVAILABLE);
            log.warn("Memory上下文查询降级，sessionCode={}, errorCode={}",
                    sessionCode, stableErrorCode(ex));
        }
        response.setMemoryLag(response.isMemoryLag() || !response.getProcessingMemories().isEmpty());
        updateCounts(response);
        return response;
    }

    @Override
    public ConversationMemoryListResponse longTermMemories(String tenantId, Long userId) {
        ConversationMemoryListResponse response = new ConversationMemoryListResponse();
        response.setGeneratedAt(Instant.now());
        response.setProcessingItems(processingItems(tenantId, userId, null, "LONG_TERM", 50));
        response.setMemoryLag(!response.getProcessingItems().isEmpty());
        if (!properties.isEnabled()) {
            response.setProviderStatus(PROVIDER_DISABLED);
            return response;
        }
        ConversationMemoryBindingEntity binding = provisionService.findActive(tenantId, userId);
        if (binding == null) {
            response.setProviderStatus(PROVIDER_BINDING_UNAVAILABLE);
            return response;
        }
        try {
            response.setItems(mapProviderItems(
                    providerAccess.listOwned(binding, tenantId, userId, MemoryScope.LONG_TERM,
                            null, 1, CONTEXT_PAGE_SIZE),
                    tenantId, userId, MemoryScope.LONG_TERM, Set.of()));
            response.setProviderStatus(PROVIDER_AVAILABLE);
        } catch (MemoryOwnershipViolationException ex) {
            response.getItems().clear();
            response.setProviderStatus(PROVIDER_SECURITY_REJECTED);
            log.warn("长期Memory归属校验失败，Provider结果已全部丢弃");
        } catch (RuntimeException ex) {
            response.setProviderStatus(PROVIDER_UNAVAILABLE);
            log.warn("长期Memory查询降级，errorCode={}", stableErrorCode(ex));
        }
        return response;
    }

    @Override
    public ConversationMemoryOperationResponse createLongTerm(String tenantId,
                                                               Long userId,
                                                               ConversationMemoryCreateRequest request) {
        requireConfirmed(request == null ? null : request.getConfirmed());
        String content = normalizeContent(request == null ? null : request.getContent());
        if (!properties.isLongTermEnabled()) {
            throw BizException.of(AiChatBizCodeConstant.MEMORY_BINDING_UNAVAILABLE);
        }
        ConversationMemoryBindingEntity binding = requireBinding(tenantId, userId);
        ProviderMemoryWriteRequest write = new ProviderMemoryWriteRequest();
        write.setMemoryIds(List.of(binding.getLongTermMemoryId()));
        write.setAgentId("platform-memory-manager");
        write.setSessionId(resolveWriteSession(null, MemoryScope.LONG_TERM));
        write.setUserId(providerAccess.providerUserId(tenantId, userId));
        write.setUserInput(content);
        write.setAgentResponse("已根据用户确认新增长期记忆。");
        MemoryWriteResponse result = callProviderWrite(() -> providerAccess.addConversation(tenantId, write));
        if (result == null || !result.isAccepted()) {
            throw BizException.of(AiChatBizCodeConstant.MEMORY_OPERATION_FAILED);
        }
        return ConversationMemoryOperationResponse.accepted(null, MemoryItemStatus.PROCESSING);
    }

    @Override
    public ConversationMemoryOperationResponse clearLongTerm(
            String tenantId, Long userId, ConversationMemoryConfirmRequest request) {
        requireConfirmed(request == null ? null : request.getConfirmed());
        if (!properties.isEnabled() || !properties.isLongTermEnabled()) {
            throw BizException.of(AiChatBizCodeConstant.MEMORY_BINDING_UNAVAILABLE);
        }
        ConversationMemoryBindingEntity migration;
        try {
            migration = provisionService.beginLongTermMigration(tenantId, userId);
        } catch (IllegalStateException ex) {
            throw BizException.of(AiChatBizCodeConstant.MEMORY_INVALID_STATE);
        }
        String oldMemoryId = migration.getLongTermMemoryId();
        MemoryDescriptor replacement = null;
        try {
            replacement = provisionService.createMemory(tenantId, userId, MemoryScope.LONG_TERM);
            if (replacement == null || !StringUtils.hasText(replacement.getMemoryId())) {
                throw new IllegalStateException("Replacement long-term Memory ID is missing");
            }
            // Create the cleanup task before switching the pointer. The retiring slot makes it
            // harmless if a scheduler sees the task during the short MIGRATING window.
            memoryBridge.enqueueLongTermMemoryDeletion(tenantId, userId, oldMemoryId);
            if (!provisionService.completeLongTermMigration(
                    tenantId, userId, oldMemoryId, replacement.getMemoryId())) {
                throw new IllegalStateException("Long-term Memory binding switch was lost");
            }
            return ConversationMemoryOperationResponse.accepted(null, MemoryItemStatus.PROCESSING);
        } catch (MemoryProviderException ex) {
            abortRotation(tenantId, userId, oldMemoryId, replacement);
            throw BizException.of(ex.isUncertain()
                    ? AiChatBizCodeConstant.MEMORY_OPERATION_FAILED
                    : AiChatBizCodeConstant.MEMORY_PROVIDER_UNAVAILABLE);
        } catch (BizException ex) {
            abortRotation(tenantId, userId, oldMemoryId, replacement);
            throw ex;
        } catch (RuntimeException ex) {
            abortRotation(tenantId, userId, oldMemoryId, replacement);
            throw BizException.of(AiChatBizCodeConstant.MEMORY_OPERATION_FAILED);
        }
    }

    private void abortRotation(String tenantId, Long userId, String oldMemoryId,
                               MemoryDescriptor replacement) {
        if (replacement != null && StringUtils.hasText(replacement.getMemoryId())
                && !replacement.getMemoryId().equals(oldMemoryId)) {
            try {
                provisionService.deleteProvisionedMemory(tenantId, replacement.getMemoryId());
            } catch (RuntimeException cleanupError) {
                log.warn("长期Memory重建失败后的新资源清理失败，errorCode={}",
                        stableErrorCode(cleanupError));
            }
        }
        provisionService.abortLongTermMigration(tenantId, userId, oldMemoryId);
    }

    @Override
    public ConversationMemoryOperationResponse disable(String tenantId, Long userId, String memoryRef) {
        OwnedMemory owned = ownedMemory(tenantId, userId, memoryRef);
        requireProviderMessage(owned);
        runProviderWrite(() -> providerAccess.updateStatus(
                tenantId, owned.reference().memoryId(), owned.reference().messageId(), false));
        return ConversationMemoryOperationResponse.accepted(memoryRef, MemoryItemStatus.DISABLED);
    }

    @Override
    public ConversationMemoryOperationResponse restore(String tenantId, Long userId, String memoryRef) {
        OwnedMemory owned = ownedMemory(tenantId, userId, memoryRef);
        requireProviderMessage(owned);
        runProviderWrite(() -> providerAccess.updateStatus(
                tenantId, owned.reference().memoryId(), owned.reference().messageId(), true));
        return ConversationMemoryOperationResponse.accepted(memoryRef, MemoryItemStatus.ACTIVE);
    }

    @Override
    public ConversationMemoryOperationResponse correct(String tenantId,
                                                        Long userId,
                                                        String memoryRef,
                                                        ConversationMemoryCorrectionRequest request) {
        requireConfirmed(request == null ? null : request.getConfirmed());
        String content = normalizeContent(request == null ? null : request.getContent());
        OwnedMemory owned = ownedMemory(tenantId, userId, memoryRef);
        MemoryMessage source = requireProviderMessage(owned);
        runProviderWrite(() -> providerAccess.updateStatus(
                tenantId, owned.reference().memoryId(), owned.reference().messageId(), false));

        ProviderMemoryWriteRequest write = new ProviderMemoryWriteRequest();
        write.setMemoryIds(List.of(owned.reference().memoryId()));
        write.setAgentId("platform-memory-corrector");
        write.setSessionId(resolveWriteSession(source.getSessionId(), owned.reference().scope()));
        write.setUserId(providerAccess.providerUserId(tenantId, userId));
        write.setUserInput(content);
        write.setAgentResponse("已按用户确认的内容修正该记忆。");
        MemoryWriteResponse result = callProviderWrite(() -> providerAccess.addConversation(tenantId, write));
        if (result == null || !result.isAccepted()) {
            throw BizException.of(AiChatBizCodeConstant.MEMORY_OPERATION_FAILED);
        }
        return ConversationMemoryOperationResponse.accepted(memoryRef, MemoryItemStatus.PROCESSING);
    }

    @Override
    public ConversationMemoryOperationResponse promote(String tenantId,
                                                        Long userId,
                                                        String memoryRef,
                                                        ConversationMemoryConfirmRequest request) {
        requireConfirmed(request == null ? null : request.getConfirmed());
        OwnedMemory owned = ownedMemory(tenantId, userId, memoryRef);
        if (owned.reference().scope() != MemoryScope.SESSION) {
            throw BizException.of(AiChatBizCodeConstant.MEMORY_INVALID_STATE, "仅会话记忆可晋升");
        }
        MemoryMessage source = requireProviderMessage(owned);
        if (Boolean.FALSE.equals(source.getEnabled()) || !StringUtils.hasText(source.getContent())
                || source.getMemoryType() == MemoryType.RAW) {
            throw BizException.of(AiChatBizCodeConstant.MEMORY_INVALID_STATE, "记忆不可晋升");
        }
        memoryBridge.enqueuePromotion(tenantId, userId, source.getSessionId(),
                owned.reference().memoryId(), owned.reference().messageId());
        return ConversationMemoryOperationResponse.accepted(memoryRef, MemoryItemStatus.PROCESSING);
    }

    @Override
    public ConversationMemoryOperationResponse excludeFromSession(String tenantId,
                                                                   Long userId,
                                                                   String memoryRef,
                                                                   ConversationMemorySessionPolicyRequest request) {
        String sessionCode = request == null ? null : request.getSessionCode();
        ownedSession(userId, sessionCode);
        OwnedMemory owned = ownedMemory(tenantId, userId, memoryRef);
        if (owned.reference().scope() != MemoryScope.LONG_TERM) {
            throw BizException.of(AiChatBizCodeConstant.MEMORY_INVALID_STATE, "仅长期记忆可按会话排除");
        }
        requireProviderMessage(owned);
        policyService.exclude(tenantId, userId, sessionCode,
                owned.reference().memoryId(), owned.reference().messageId());
        return ConversationMemoryOperationResponse.accepted(memoryRef, MemoryItemStatus.ACTIVE);
    }

    @Override
    public ConversationMemoryOperationResponse forget(String tenantId, Long userId, String memoryRef) {
        OwnedMemory owned = ownedMemory(tenantId, userId, memoryRef);
        requireProviderMessage(owned);
        runProviderWrite(() -> providerAccess.forget(
                tenantId, owned.reference().memoryId(), owned.reference().messageId()));
        policyService.removeForMessage(tenantId, userId,
                owned.reference().memoryId(), owned.reference().messageId());
        return ConversationMemoryOperationResponse.accepted(memoryRef, MemoryItemStatus.FORGOTTEN);
    }

    private OwnedMemory ownedMemory(String tenantId, Long userId, String memoryRef) {
        if (!StringUtils.hasText(memoryRef)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_MEMORY_REF);
        }
        try {
            MemoryReference reference = referenceCodec.decode(memoryRef);
            if (!tenantId.equals(reference.tenantId()) || !userId.equals(reference.userId())) {
                throw new MemoryOwnershipViolationException();
            }
            ConversationMemoryBindingEntity binding = requireBinding(tenantId, userId);
            String expectedMemoryId = providerAccess.requireOwnedMemory(
                    binding, tenantId, userId, reference.scope());
            if (!expectedMemoryId.equals(reference.memoryId())) {
                throw new MemoryOwnershipViolationException();
            }
            if (reference.scope() == MemoryScope.SESSION) {
                ownedSession(userId, reference.sessionId());
            }
            return new OwnedMemory(binding, reference);
        } catch (BizException ex) {
            throw ex;
        } catch (IllegalArgumentException | MemoryOwnershipViolationException ex) {
            throw BizException.of(AiChatBizCodeConstant.MEMORY_NOT_FOUND);
        }
    }

    private MemoryMessage requireProviderMessage(OwnedMemory owned) {
        try {
            return providerAccess.requireOwnedMessage(
                    owned.binding(), owned.reference().tenantId(), owned.reference().userId(),
                    owned.reference().scope(), owned.reference().sessionId(), owned.reference().messageId());
        } catch (MemoryNotFoundException | MemoryOwnershipViolationException ex) {
            throw BizException.of(AiChatBizCodeConstant.MEMORY_NOT_FOUND);
        } catch (MemoryProviderException ex) {
            throw BizException.of(AiChatBizCodeConstant.MEMORY_PROVIDER_UNAVAILABLE);
        }
    }

    private ConversationMemoryBindingEntity requireBinding(String tenantId, Long userId) {
        if (!properties.isEnabled()) {
            throw BizException.of(AiChatBizCodeConstant.MEMORY_BINDING_UNAVAILABLE);
        }
        ConversationMemoryBindingEntity binding = provisionService.findActive(tenantId, userId);
        if (binding == null) {
            throw BizException.of(AiChatBizCodeConstant.MEMORY_BINDING_UNAVAILABLE);
        }
        return binding;
    }

    private ConversationSessionDTO ownedSession(Long userId, String sessionCode) {
        if (!StringUtils.hasText(sessionCode)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_SESSION_CODE);
        }
        ConversationHistoryQueryRequest query = new ConversationHistoryQueryRequest();
        query.setSessionCode(sessionCode.trim());
        query.setUserId(userId);
        ConversationSessionDTO session = sessionService.get(query);
        if (session == null) {
            throw BizException.of(AiChatBizCodeConstant.CONVERSATION_NOT_FOUND, sessionCode);
        }
        return session;
    }

    private List<ConversationMemoryItem> mapProviderItems(List<MemoryMessage> messages,
                                                          String tenantId,
                                                          Long userId,
                                                          MemoryScope scope,
                                                          Set<String> excluded) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        return messages.stream()
                .filter(Objects::nonNull)
                .filter(item -> visibleType(scope, item.getMemoryType()))
                .filter(item -> StringUtils.hasText(item.getMessageId()))
                .filter(item -> StringUtils.hasText(item.getContent()) || processing(item))
                .map(item -> toItem(item, tenantId, userId, scope, excluded))
                .sorted(Comparator.comparing(ConversationMemoryItem::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private ConversationMemoryItem toItem(MemoryMessage message,
                                           String tenantId,
                                           Long userId,
                                           MemoryScope scope,
                                           Set<String> excluded) {
        ConversationMemoryItem item = new ConversationMemoryItem();
        item.setScope(scope);
        item.setMemoryType(message.getMemoryType());
        item.setStatus(Boolean.FALSE.equals(message.getEnabled())
                ? MemoryItemStatus.DISABLED
                : processing(message) ? MemoryItemStatus.PROCESSING : MemoryItemStatus.ACTIVE);
        item.setContent(limitContent(message.getContent()));
        item.setSourceSessionCode(message.getSessionId());
        item.setSourceRoundCode(roundCode(message.getSourceId()));
        item.setCreatedAt(message.getCreatedAt());
        item.setExcludedFromSession(excluded.contains(policyService.key(
                message.getMemoryId(), message.getMessageId())));
        item.setMemoryRef(referenceCodec.encode(new MemoryReference(
                tenantId, userId, scope, message.getMemoryId(), message.getMessageId(),
                message.getSessionId(), message.getMemoryType())));
        return item;
    }

    private List<ConversationMemoryItem> processingItems(String tenantId,
                                                          Long userId,
                                                          String sessionCode,
                                                          String targetScope,
                                                          int limit) {
        return taskDataService.findOutstanding(tenantId, userId, sessionCode, targetScope, limit).stream()
                .map(this::processingItem)
                .toList();
    }

    private ConversationMemoryItem processingItem(ConversationMemorySyncTaskEntity task) {
        ConversationMemoryItem item = new ConversationMemoryItem();
        item.setScope("LONG_TERM".equals(task.getTargetScope())
                ? MemoryScope.LONG_TERM : MemoryScope.SESSION);
        item.setStatus(MemoryItemStatus.PROCESSING);
        item.setSourceSessionCode(task.getSessionCode());
        item.setSourceRoundCode(task.getRoundCode());
        item.setCreatedAt(task.getCreateTime() == null
                ? null : task.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant());
        return item;
    }

    private void distribute(List<ConversationMemoryItem> source,
                            List<ConversationMemoryItem> active,
                            List<ConversationMemoryItem> processing,
                            List<ConversationMemoryItem> disabled) {
        for (ConversationMemoryItem item : source) {
            if (item.getStatus() == MemoryItemStatus.DISABLED) {
                disabled.add(item);
            } else if (item.getStatus() == MemoryItemStatus.PROCESSING) {
                processing.add(item);
            } else {
                active.add(item);
            }
        }
    }

    private void updateCounts(ConversationMemoryContextResponse response) {
        ConversationMemoryCounts counts = response.getCounts();
        counts.setSessionMemories(response.getSessionMemories().size());
        counts.setLongTermMemories(response.getLongTermMemories().size());
        counts.setProcessing(response.getProcessingMemories().size());
        counts.setDisabled(response.getDisabledMemories().size());
    }

    private boolean visibleType(MemoryScope scope, MemoryType type) {
        if (type == null || type == MemoryType.RAW) {
            return false;
        }
        return scope == MemoryScope.SESSION
                ? type == MemoryType.SEMANTIC || type == MemoryType.EPISODIC
                : type == MemoryType.SEMANTIC || type == MemoryType.PROCEDURAL;
    }

    private boolean processing(MemoryMessage item) {
        if (!StringUtils.hasText(item.getProcessingStatus())) {
            return false;
        }
        String value = item.getProcessingStatus().trim().toUpperCase(java.util.Locale.ROOT);
        return !(value.contains("COMPLETE") || value.contains("SUCCESS") || value.contains("DONE"));
    }

    private String limitContent(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String value = content.trim();
        int count = value.codePointCount(0, value.length());
        if (count <= MAX_VISIBLE_CONTENT_CODEPOINTS) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, MAX_VISIBLE_CONTENT_CODEPOINTS)) + "…";
    }

    private String normalizeContent(String content) {
        String value = limitContent(content);
        if (!StringUtils.hasText(value)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_CONTENT);
        }
        return value;
    }

    private void requireConfirmed(Boolean confirmed) {
        if (!Boolean.TRUE.equals(confirmed)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_MEMORY_CONFIRMATION);
        }
    }

    private String resolveWriteSession(String sessionId, MemoryScope scope) {
        if (StringUtils.hasText(sessionId)) {
            return sessionId.trim();
        }
        return scope == MemoryScope.LONG_TERM ? "long-term-manual" : "memory-correction";
    }

    private String roundCode(String sourceId) {
        return StringUtils.hasText(sourceId) && sourceId.startsWith("round-") ? sourceId : null;
    }

    private void runProviderWrite(Runnable action) {
        callProviderWrite(() -> {
            action.run();
            return Boolean.TRUE;
        });
    }

    private <T> T callProviderWrite(java.util.concurrent.Callable<T> action) {
        try {
            return action.call();
        } catch (MemoryProviderException ex) {
            throw BizException.of(ex.isUncertain()
                    ? AiChatBizCodeConstant.MEMORY_OPERATION_FAILED
                    : AiChatBizCodeConstant.MEMORY_PROVIDER_UNAVAILABLE);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.of(AiChatBizCodeConstant.MEMORY_OPERATION_FAILED);
        }
    }

    private String stableErrorCode(Throwable error) {
        return error instanceof MemoryProviderException providerException
                ? providerException.getErrorCode() : error.getClass().getSimpleName();
    }

    private record OwnedMemory(ConversationMemoryBindingEntity binding, MemoryReference reference) {
    }
}
