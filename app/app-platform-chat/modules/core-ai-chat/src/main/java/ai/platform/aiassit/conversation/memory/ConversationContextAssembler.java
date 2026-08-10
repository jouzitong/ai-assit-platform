package ai.platform.aiassit.conversation.memory;

import ai.platform.aiassit.conversation.data.entity.ConversationMemoryBindingEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationMessageDTO;
import ai.platform.aiassit.conversation.data.service.ConversationMemorySyncTaskDataService;
import ai.platform.aiassit.conversation.memory.config.ConversationMemoryProperties;
import ai.platform.aiassit.conversation.workflow.context.ConversationContextPackage;
import ai.platform.aiassit.conversation.workflow.context.ConversationMemoryContextItem;
import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;
import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryScope;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryType;
import ai.platform.aiassit.service.ai.spi.memory.MemoryService;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryMessage;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemorySearchResponse;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemorySearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/** Searches externally owned memories, applies hard ownership filters, and builds a transient package. */
@Slf4j
@Service
public class ConversationContextAssembler {

    private final ConversationMemoryProvisionService provisionService;
    private final ConversationMemoryProviderRegistry providerRegistry;
    private final ConversationMemoryIdentity identity;
    private final ConversationMemorySyncTaskDataService taskDataService;
    private final MemorySessionPolicyService policyService;
    private final ContextBudgetPlanner budgetPlanner;
    private final ConversationMemoryProperties properties;
    private final Executor executor;

    public ConversationContextAssembler(ConversationMemoryProvisionService provisionService,
                                        ConversationMemoryProviderRegistry providerRegistry,
                                        ConversationMemoryIdentity identity,
                                        ConversationMemorySyncTaskDataService taskDataService,
                                        MemorySessionPolicyService policyService,
                                        ContextBudgetPlanner budgetPlanner,
                                        ConversationMemoryProperties properties,
                                        @Qualifier("conversationMemoryExecutor") Executor executor) {
        this.provisionService = provisionService;
        this.providerRegistry = providerRegistry;
        this.identity = identity;
        this.taskDataService = taskDataService;
        this.policyService = policyService;
        this.budgetPlanner = budgetPlanner;
        this.properties = properties;
        this.executor = executor;
    }

    public void assemble(ConversationRuntimeContext context) {
        ConversationContextPackage result = new ConversationContextPackage();
        context.setContextPackage(result);
        if (!properties.isEnabled() || !properties.getRecall().isEnabled()
                || context.getSession() == null || context.getCommand() == null) {
            return;
        }
        String tenantId = context.getTenantId();
        Long userId = context.getSession().getUserId();
        String sessionCode = context.getSession().getSessionCode();
        result.setMemoryLag(taskDataService.hasOutstanding(tenantId, userId, sessionCode));
        ConversationMemoryBindingEntity binding = provisionService.findActive(tenantId, userId);
        if (binding == null) {
            result.setDegradedReason("MEMORY_BINDING_UNAVAILABLE");
            return;
        }
        long startedAt = System.currentTimeMillis();
        try {
            RequestMeta meta = provisionService.providerMeta(tenantId);
            meta.setExt(new LinkedHashMap<>(meta.getExt() == null ? java.util.Map.of() : meta.getExt()));
            meta.getExt().put("memoryTimeoutMs", properties.getRecall().getTimeoutMs());
            MemoryService provider = providerRegistry.require(properties.getProviderType());
            String providerUserId = identity.providerUserId(tenantId, userId);
            String query = context.getCommand().getMessage();
            CompletableFuture<ScopeResult> sessionFuture = searchAsync(provider, meta, MemoryScope.SESSION,
                    binding.getSessionMemoryId(), sessionCode, providerUserId, query,
                    properties.getRecall().getSessionTopN());
            CompletableFuture<ScopeResult> longTermFuture = searchAsync(provider, meta, MemoryScope.LONG_TERM,
                    binding.getLongTermMemoryId(), null, providerUserId, query,
                    properties.getRecall().getLongTermTopN());
            ScopeResult sessionResult = sessionFuture.join();
            ScopeResult longTermResult = longTermFuture.join();
            result.setProviderLatencyMs(System.currentTimeMillis() - startedAt);
            if (sessionResult.ownershipMismatch() || longTermResult.ownershipMismatch()) {
                result.setDegradedReason("MEMORY_OWNERSHIP_MISMATCH");
                log.warn("Memory召回归属校验失败，sessionCode={}，所有候选已丢弃", sessionCode);
                return;
            }
            if (sessionResult.failed() || longTermResult.failed()) {
                result.setDegradedReason("MEMORY_PROVIDER_UNAVAILABLE");
            }
            Set<String> excluded = policyService.excludedMessageKeys(tenantId, userId, sessionCode);
            Set<String> recentContent = recentContent(context);
            List<MemoryMessage> sessionCandidates = filter(sessionResult.items(), MemoryScope.SESSION,
                    binding.getSessionMemoryId(), sessionCode, excluded, recentContent);
            List<MemoryMessage> longTermCandidates = filter(longTermResult.items(), MemoryScope.LONG_TERM,
                    binding.getLongTermMemoryId(), null, excluded, recentContent);
            preferLongTermDuplicates(sessionCandidates, longTermCandidates);
            result.setSessionCandidateCount(sessionCandidates.size());
            result.setLongTermCandidateCount(longTermCandidates.size());
            ContextBudgetPlanner.BudgetResult selected = budgetPlanner.select(sessionCandidates, longTermCandidates);
            result.setSessionMemories(toContextItems(selected.sessionMessages(), MemoryScope.SESSION));
            result.setLongTermMemories(toContextItems(selected.longTermMessages(), MemoryScope.LONG_TERM));
            result.setInjectionEnabled(properties.getRecall().isInjectionEnabled());
            log.info("Memory召回完成，sessionCode={}, sessionCandidates={}, longTermCandidates={}, selected={}, durationMs={}, injectionEnabled={}",
                    sessionCode, sessionCandidates.size(), longTermCandidates.size(),
                    result.getSessionMemories().size() + result.getLongTermMemories().size(),
                    result.getProviderLatencyMs(), result.isInjectionEnabled());
        } catch (RuntimeException ex) {
            result.setProviderLatencyMs(System.currentTimeMillis() - startedAt);
            result.setDegradedReason("MEMORY_PROVIDER_UNAVAILABLE");
            log.warn("Memory召回降级，sessionCode={}, durationMs={}, errorCode={}",
                    sessionCode, result.getProviderLatencyMs(), ex.getClass().getSimpleName());
        }
    }

    private CompletableFuture<ScopeResult> searchAsync(MemoryService provider,
                                                       RequestMeta meta,
                                                       MemoryScope scope,
                                                       String memoryId,
                                                       String sessionId,
                                                       String providerUserId,
                                                       String query,
                                                       int topN) {
        if (!StringUtils.hasText(memoryId)
                || (scope == MemoryScope.SESSION && !properties.isSessionEnabled())
                || (scope == MemoryScope.LONG_TERM && !properties.isLongTermEnabled())) {
            return CompletableFuture.completedFuture(ScopeResult.success(List.of()));
        }
        return CompletableFuture.supplyAsync(() -> search(
                        provider, meta, memoryId, sessionId, providerUserId, query, topN), executor)
                .handle((value, error) -> error == null ? value : ScopeResult.failure())
                .completeOnTimeout(ScopeResult.failure(),
                        Math.max(100, properties.getRecall().getTimeoutMs() + 100L), TimeUnit.MILLISECONDS);
    }

    private ScopeResult search(MemoryService provider,
                               RequestMeta meta,
                               String memoryId,
                               String sessionId,
                               String providerUserId,
                               String query,
                               int topN) {
        ProviderMemorySearchRequest request = new ProviderMemorySearchRequest();
        request.setMeta(meta);
        request.setQuery(query);
        request.setMemoryIds(List.of(memoryId));
        request.setSessionId(sessionId);
        request.setUserId(providerUserId);
        request.setSimilarityThreshold(properties.getRecall().getSimilarityThreshold());
        request.setKeywordsSimilarityWeight(properties.getRecall().getKeywordWeight());
        request.setTopN(topN);
        MemorySearchResponse response = provider.searchMessages(request);
        List<MemoryMessage> items = response == null || response.getItems() == null
                ? List.of() : response.getItems();
        boolean mismatch = items.stream().anyMatch(item -> ownershipMismatch(
                item, memoryId));
        return mismatch ? ScopeResult.mismatch() : ScopeResult.success(items);
    }

    private boolean ownershipMismatch(MemoryMessage item,
                                      String memoryId) {
        if (item == null) {
            return false;
        }
        return StringUtils.hasText(item.getMemoryId()) && !memoryId.equals(item.getMemoryId());
    }

    private List<MemoryMessage> filter(List<MemoryMessage> items,
                                       MemoryScope scope,
                                       String expectedMemoryId,
                                       String expectedSessionId,
                                       Set<String> excluded,
                                       Set<String> recentContent) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> messageIds = new HashSet<>();
        Set<String> contentKeys = new HashSet<>();
        List<MemoryMessage> result = new ArrayList<>();
        for (MemoryMessage item : items) {
            if (item == null || Boolean.FALSE.equals(item.getEnabled()) || !StringUtils.hasText(item.getContent())
                    || !allowedType(item.getMemoryType(), scope)) {
                continue;
            }
            if ((StringUtils.hasText(item.getMemoryId()) && !expectedMemoryId.equals(item.getMemoryId()))
                    || (StringUtils.hasText(expectedSessionId) && !expectedSessionId.equals(item.getSessionId()))) {
                continue;
            }
            if (!StringUtils.hasText(item.getMemoryId())) {
                item.setMemoryId(expectedMemoryId);
            }
            if (StringUtils.hasText(item.getMessageId())
                    && !messageIds.add(item.getMemoryId() + "\u001f" + item.getMessageId())) {
                continue;
            }
            if (excluded.contains(policyService.key(item.getMemoryId(), item.getMessageId()))) {
                continue;
            }
            String contentKey = normalizeContent(item.getContent());
            if (contentKey.isEmpty() || recentContent.contains(contentKey) || !contentKeys.add(contentKey)) {
                continue;
            }
            result.add(item);
        }
        return result;
    }

    private boolean allowedType(MemoryType type, MemoryScope scope) {
        if (type == null || type == MemoryType.RAW) {
            return false;
        }
        return scope == MemoryScope.SESSION
                ? type == MemoryType.SEMANTIC || type == MemoryType.EPISODIC
                : type == MemoryType.SEMANTIC || type == MemoryType.PROCEDURAL;
    }

    private void preferLongTermDuplicates(List<MemoryMessage> session, List<MemoryMessage> longTerm) {
        Set<String> longTermContent = new HashSet<>();
        longTerm.forEach(item -> longTermContent.add(normalizeContent(item.getContent())));
        session.removeIf(item -> longTermContent.contains(normalizeContent(item.getContent())));
    }

    private Set<String> recentContent(ConversationRuntimeContext context) {
        Set<String> result = new LinkedHashSet<>();
        List<ConversationMessageDTO> messages = context.getOrCreateUserMessageContext().getSessionMessages();
        if (messages != null) {
            messages.stream().filter(java.util.Objects::nonNull)
                    .map(ConversationMessageDTO::getContent)
                    .map(this::normalizeContent)
                    .filter(StringUtils::hasText)
                    .forEach(result::add);
        }
        return result;
    }

    private String normalizeContent(String content) {
        return StringUtils.hasText(content)
                ? content.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT) : "";
    }

    private List<ConversationMemoryContextItem> toContextItems(List<MemoryMessage> messages, MemoryScope scope) {
        if (messages == null) {
            return List.of();
        }
        return messages.stream().map(item -> {
            ConversationMemoryContextItem result = new ConversationMemoryContextItem();
            result.setScope(scope.name());
            result.setMemoryType(item.getMemoryType() == null ? null : item.getMemoryType().name());
            result.setContent(item.getContent());
            result.setSourceSessionCode(item.getSessionId());
            result.setCreatedAt(item.getCreatedAt());
            return result;
        }).toList();
    }

    private record ScopeResult(List<MemoryMessage> items, boolean failed, boolean ownershipMismatch) {
        private static ScopeResult success(List<MemoryMessage> items) {
            return new ScopeResult(items, false, false);
        }

        private static ScopeResult failure() {
            return new ScopeResult(List.of(), true, false);
        }

        private static ScopeResult mismatch() {
            return new ScopeResult(List.of(), false, true);
        }
    }
}
