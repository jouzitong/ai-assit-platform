package ai.platform.aiassit.conversation.memory;

import ai.platform.aiassit.conversation.data.entity.ConversationMemoryBindingEntity;
import ai.platform.aiassit.conversation.data.entity.ConversationMemorySyncTaskEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationMessageDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationRoundDTO;
import ai.platform.aiassit.conversation.data.service.ConversationMemorySyncTaskDataService;
import ai.platform.aiassit.conversation.data.service.ConversationMessageService;
import ai.platform.aiassit.conversation.data.service.ConversationRoundService;
import ai.platform.aiassit.conversation.memory.config.ConversationMemoryProperties;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryScope;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryType;
import ai.platform.aiassit.service.ai.spi.memory.MemoryProviderException;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryMessage;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryWriteResponse;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryWriteRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** Pulls authoritative round text only at execution time and sends it directly to RAGFlow. */
@Slf4j
@Service
public class ConversationMemorySyncWorker {

    private final ConversationMemorySyncTaskDataService taskDataService;
    private final ConversationRoundService roundService;
    private final ConversationMessageService messageService;
    private final ConversationMemoryProvisionService provisionService;
    private final ConversationMemoryProviderAccess providerAccess;
    private final ConversationMemoryProperties properties;

    public ConversationMemorySyncWorker(ConversationMemorySyncTaskDataService taskDataService,
                                        ConversationRoundService roundService,
                                        ConversationMessageService messageService,
                                        ConversationMemoryProvisionService provisionService,
                                        ConversationMemoryProviderAccess providerAccess,
                                        ConversationMemoryProperties properties) {
        this.taskDataService = taskDataService;
        this.roundService = roundService;
        this.messageService = messageService;
        this.provisionService = provisionService;
        this.providerAccess = providerAccess;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${ai.chat.memory.sync.fixed-delay-ms:5000}")
    public void poll() {
        if (!properties.isEnabled() || !properties.getSync().isShadowWriteEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int uncertain = taskDataService.recoverExpiredLeases(now);
        if (uncertain > 0) {
            log.warn("Memory同步发现租约过期任务，count={}，已转为UNKNOWN等待对账", uncertain);
        }
        for (ConversationMemorySyncTaskEntity task : taskDataService.findClaimable(
                now, properties.getSync().getBatchSize())) {
            LocalDateTime leaseUntil = now.plus(properties.getSync().getLeaseTimeout());
            if (!taskDataService.claim(task.getId(), now, leaseUntil)) {
                continue;
            }
            process(task);
        }
    }

    void process(ConversationMemorySyncTaskEntity task) {
        try {
            if ("UNKNOWN".equalsIgnoreCase(task.getStatus())) {
                reconcileUnknown(task);
                return;
            }
            switch (task.getOperation()) {
                case "ADD_ROUND" -> processRound(task);
                case "PROMOTE" -> processPromotion(task);
                case "DELETE_SESSION" -> processSessionDeletion(task);
                case "DELETE_MEMORY" -> processRetiredMemory(task);
                default -> markDead(task, "MEMORY_SYNC_OPERATION_UNSUPPORTED");
            }
        } catch (MemoryProviderException ex) {
            if (ex.isUncertain()) {
                if ("UNKNOWN".equalsIgnoreCase(task.getStatus())) {
                    deferUnknown(task, ex.getErrorCode());
                } else {
                    markUnknown(task, ex.getErrorCode());
                }
                log.warn("Memory同步结果不确定，taskCode={}, errorCode={}", task.getTaskCode(), ex.getErrorCode());
            } else {
                retry(task, ex.getErrorCode());
            }
        } catch (ConversationMemoryProviderAccess.MemoryNotFoundException
                 | ConversationMemoryProviderAccess.MemoryOwnershipViolationException ex) {
            markDead(task, "MEMORY_SOURCE_INVALID");
        } catch (RuntimeException ex) {
            if ("UNKNOWN".equalsIgnoreCase(task.getStatus())) {
                deferUnknown(task, "MEMORY_SYNC_PROCESS_FAILED");
            } else {
                retry(task, "MEMORY_SYNC_PROCESS_FAILED");
            }
        }
    }

    /**
     * Reconciles an uncertain task before any write can be replayed. A missing Provider
     * idempotency contract is deliberately treated as a reason to wait/dead-letter, never as
     * permission to issue a blind duplicate POST.
     */
    private void reconcileUnknown(ConversationMemorySyncTaskEntity task) {
        if ("DELETE_SESSION".equals(task.getOperation())) {
            // Message deletion is idempotent: it is safe to continue the cleanup saga.
            processSessionDeletion(task);
            return;
        }
        if ("DELETE_MEMORY".equals(task.getOperation())) {
            processRetiredMemory(task);
            return;
        }
        if (!properties.getSync().isProviderIdempotencyEnabled()) {
            deferUnknown(task, "MEMORY_PROVIDER_IDEMPOTENCY_UNAVAILABLE");
            return;
        }
        MemoryScope scope = "LONG_TERM".equals(task.getTargetScope())
                ? MemoryScope.LONG_TERM : MemoryScope.SESSION;
        ConversationMemoryBindingEntity binding = provisionService.findActive(
                task.getTenantId(), task.getUserId());
        if (binding == null || !StringUtils.hasText(task.getTargetMemoryId())) {
            deferUnknown(task, "MEMORY_BINDING_UNAVAILABLE");
            return;
        }
        MemoryMessage found;
        try {
            found = providerAccess.findOwnedByExternalId(
                    binding, task.getTenantId(), task.getUserId(), scope,
                    scope == MemoryScope.SESSION ? task.getSessionCode() : null,
                    task.getIdempotencyKey());
        } catch (MemoryProviderException ex) {
            // A failed lookup cannot prove absence, even when the lookup error itself is a 4xx.
            deferUnknown(task, ex.getErrorCode());
            return;
        }
        if (found != null) {
            markSucceeded(task, found.getMessageId());
            return;
        }
        // The Provider answered successfully and did not return the locator: only now is retry
        // safe, because the configured contract says the lookup is authoritative.
        retry(task, "MEMORY_EXTERNAL_ID_NOT_FOUND");
    }

    private void processRound(ConversationMemorySyncTaskEntity task) {
        if (!"SESSION".equals(task.getTargetScope())) {
            markDead(task, "MEMORY_SYNC_OPERATION_UNSUPPORTED");
            return;
        }
        ConversationRoundDTO round = roundService.queryOwned(
                task.getRoundCode(), task.getSessionCode(), task.getUserId());
        if (round == null || (!"SUCCESS".equalsIgnoreCase(round.getStatus())
                && !"INPUT_REQUIRED".equalsIgnoreCase(round.getStatus()))) {
            markDead(task, "MEMORY_SOURCE_INVALID");
            return;
        }
        List<ConversationMessageDTO> messages = messageService.queryByRoundCode(task.getRoundCode()).stream()
                .filter(message -> task.getSessionCode().equals(message.getSessionCode()))
                .toList();
        String userInput = lastContent(messages, "USER");
        String assistantResponse = lastContent(messages, "ASSISTANT");
        if (!StringUtils.hasText(userInput) || !StringUtils.hasText(assistantResponse)) {
            markDead(task, "MEMORY_SOURCE_INVALID");
            return;
        }
        ConversationMemoryBindingEntity binding = provisionService.ensureBinding(
                task.getTenantId(), task.getUserId());
        task.setTargetMemoryId(binding.getSessionMemoryId());
        ProviderMemoryWriteRequest request = writeRequest(task, binding.getSessionMemoryId(),
                StringUtils.hasText(round.getRootAgentCode()) ? round.getRootAgentCode() : "platform-chat",
                userInput, assistantResponse);
        accept(task, providerAccess.addConversation(task.getTenantId(), request));
    }

    private void processPromotion(ConversationMemorySyncTaskEntity task) {
        if (!"LONG_TERM".equals(task.getTargetScope())
                || !StringUtils.hasText(task.getSourceMemoryId())
                || !StringUtils.hasText(task.getSourceMessageId())) {
            markDead(task, "MEMORY_SYNC_OPERATION_UNSUPPORTED");
            return;
        }
        ConversationMemoryBindingEntity binding = provisionService.ensureBinding(
                task.getTenantId(), task.getUserId());
        if (!task.getSourceMemoryId().equals(binding.getSessionMemoryId())) {
            markDead(task, "MEMORY_SOURCE_INVALID");
            return;
        }
        task.setTargetMemoryId(binding.getLongTermMemoryId());
        MemoryMessage source = providerAccess.requireOwnedMessage(
                binding, task.getTenantId(), task.getUserId(), MemoryScope.SESSION,
                task.getSessionCode(), task.getSourceMessageId());
        if (Boolean.FALSE.equals(source.getEnabled()) || !StringUtils.hasText(source.getContent())
                || source.getMemoryType() == null || source.getMemoryType() == MemoryType.RAW) {
            markDead(task, "MEMORY_SOURCE_INVALID");
            return;
        }
        ProviderMemoryWriteRequest request = writeRequest(task, binding.getLongTermMemoryId(),
                "platform-memory-promoter", source.getContent(),
                "已确认将该信息作为长期记忆使用。");
        accept(task, providerAccess.addConversation(task.getTenantId(), request));
    }

    private void processSessionDeletion(ConversationMemorySyncTaskEntity task) {
        if (!"SESSION".equals(task.getTargetScope()) || !StringUtils.hasText(task.getSessionCode())) {
            markDead(task, "MEMORY_SYNC_OPERATION_UNSUPPORTED");
            return;
        }
        // A cleanup task must never provision a new Memory after the chat session is deleted.
        ConversationMemoryBindingEntity binding = provisionService.findActive(
                task.getTenantId(), task.getUserId());
        if (binding == null) {
            markSucceeded(task, null);
            return;
        }
        providerAccess.forgetOwnedSession(binding, task.getTenantId(), task.getUserId(),
                task.getSessionCode(), task.getTargetMemoryId());
        markSucceeded(task, null);
    }

    private void processRetiredMemory(ConversationMemorySyncTaskEntity task) {
        if (!"LONG_TERM".equals(task.getTargetScope()) || !StringUtils.hasText(task.getTargetMemoryId())) {
            markDead(task, "MEMORY_SYNC_OPERATION_UNSUPPORTED");
            return;
        }
        ConversationMemoryBindingEntity binding = provisionService.findActive(
                task.getTenantId(), task.getUserId());
        if (binding == null) {
            if ("UNKNOWN".equalsIgnoreCase(task.getStatus())) {
                deferUnknown(task, "MEMORY_BINDING_UNAVAILABLE");
            } else {
                retry(task, "MEMORY_BINDING_UNAVAILABLE");
            }
            return;
        }
        // If a migration was aborted or another retry already completed cleanup, the old ID is
        // no longer a deletable resource from this binding. Treat the task as an idempotent no-op.
        if (!task.getTargetMemoryId().equals(binding.getRetiringLongTermMemoryId())) {
            markSucceeded(task, null);
            return;
        }
        providerAccess.deleteRetiredMemory(binding, task.getTenantId(), task.getUserId(),
                task.getTargetMemoryId());
        if (!provisionService.clearRetiringLongTermMemory(binding, task.getTargetMemoryId())) {
            if ("UNKNOWN".equalsIgnoreCase(task.getStatus())) {
                deferUnknown(task, "MEMORY_BINDING_UPDATE_UNCERTAIN");
            } else {
                retry(task, "MEMORY_BINDING_UPDATE_UNCERTAIN");
            }
            return;
        }
        markSucceeded(task, null);
    }

    private ProviderMemoryWriteRequest writeRequest(ConversationMemorySyncTaskEntity task,
                                                     String memoryId,
                                                     String agentId,
                                                     String userInput,
                                                     String assistantResponse) {
        ProviderMemoryWriteRequest request = new ProviderMemoryWriteRequest();
        request.setMemoryIds(List.of(memoryId));
        request.setAgentId(agentId);
        request.setSessionId(task.getSessionCode());
        request.setUserId(providerAccess.providerUserId(task.getTenantId(), task.getUserId()));
        request.setExternalId(task.getIdempotencyKey());
        request.setUserInput(userInput);
        request.setAgentResponse(assistantResponse);
        return request;
    }

    private void accept(ConversationMemorySyncTaskEntity task, MemoryWriteResponse response) {
        if (response == null || !response.isAccepted()) {
            retry(task, "MEMORY_PROVIDER_REJECTED");
            return;
        }
        task.setStatus("SUCCEEDED");
        task.setProviderMessageId(response.getProviderMessageId());
        task.setLastErrorCode(null);
        task.setLeaseUntil(null);
        task.setNextRetryAt(null);
        task.setFinishedAt(LocalDateTime.now());
        taskDataService.update(task);
    }

    private void markUnknown(ConversationMemorySyncTaskEntity task, String errorCode) {
        task.setStatus("UNKNOWN");
        task.setLastErrorCode(errorCode);
        task.setLeaseUntil(null);
        task.setNextRetryAt(LocalDateTime.now().plus(unknownBackoff(0)));
        taskDataService.update(task);
    }

    private void deferUnknown(ConversationMemorySyncTaskEntity task, String errorCode) {
        int attempts = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        task.setRetryCount(attempts);
        task.setLastErrorCode(errorCode);
        task.setLeaseUntil(null);
        if (attempts >= properties.getSync().getMaxRetries()) {
            markDead(task, errorCode);
            return;
        }
        task.setStatus("UNKNOWN");
        task.setNextRetryAt(LocalDateTime.now().plus(unknownBackoff(attempts)));
        taskDataService.update(task);
    }

    private Duration unknownBackoff(int attempts) {
        Duration base = properties.getSync().getRetryBaseDelay();
        long multiplier = 1L << Math.min(Math.max(attempts, 0), 8);
        Duration delay = base.multipliedBy(multiplier);
        return delay.compareTo(Duration.ofHours(6)) > 0 ? Duration.ofHours(6) : delay;
    }

    private void markSucceeded(ConversationMemorySyncTaskEntity task, String providerMessageId) {
        task.setStatus("SUCCEEDED");
        task.setProviderMessageId(providerMessageId);
        task.setLastErrorCode(null);
        task.setLeaseUntil(null);
        task.setNextRetryAt(null);
        task.setFinishedAt(LocalDateTime.now());
        taskDataService.update(task);
    }

    private String lastContent(List<ConversationMessageDTO> messages, String role) {
        String result = null;
        for (ConversationMessageDTO message : messages) {
            if (message != null && role.equalsIgnoreCase(message.getRole())
                    && StringUtils.hasText(message.getContent())) {
                result = message.getContent();
            }
        }
        return result;
    }

    private void retry(ConversationMemorySyncTaskEntity task, String errorCode) {
        int retries = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        task.setRetryCount(retries);
        task.setLastErrorCode(errorCode);
        task.setLeaseUntil(null);
        if (retries >= properties.getSync().getMaxRetries()) {
            markDead(task, errorCode);
            return;
        }
        task.setStatus("RETRY");
        task.setNextRetryAt(LocalDateTime.now().plus(backoff(retries)));
        taskDataService.update(task);
        log.warn("Memory同步等待重试，taskCode={}, retryCount={}, errorCode={}",
                task.getTaskCode(), retries, errorCode);
    }

    private Duration backoff(int retries) {
        long multiplier = 1L << Math.min(Math.max(retries - 1, 0), 8);
        Duration delay = properties.getSync().getRetryBaseDelay().multipliedBy(multiplier);
        return delay.compareTo(Duration.ofHours(1)) > 0 ? Duration.ofHours(1) : delay;
    }

    private void markDead(ConversationMemorySyncTaskEntity task, String errorCode) {
        task.setStatus("DEAD");
        task.setLastErrorCode(errorCode);
        task.setLeaseUntil(null);
        task.setNextRetryAt(null);
        task.setFinishedAt(LocalDateTime.now());
        taskDataService.update(task);
        log.warn("Memory同步任务终止，taskCode={}, errorCode={}", task.getTaskCode(), errorCode);
    }
}
