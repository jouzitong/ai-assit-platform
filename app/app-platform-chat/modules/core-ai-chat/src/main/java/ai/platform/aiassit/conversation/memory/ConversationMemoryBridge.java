package ai.platform.aiassit.conversation.memory;

import ai.platform.aiassit.agent.runtime.AgentConversationOutcome;
import ai.platform.aiassit.conversation.data.entity.ConversationMemoryBindingEntity;
import ai.platform.aiassit.conversation.data.entity.ConversationMemorySyncTaskEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationRoundDTO;
import ai.platform.aiassit.conversation.data.service.ConversationMemorySyncTaskDataService;
import ai.platform.aiassit.conversation.memory.config.ConversationMemoryProperties;
import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Conversation-facing bridge. It only creates locator-based tasks and transient context packages. */
@Slf4j
@Service
public class ConversationMemoryBridge {

    private final ConversationMemorySyncTaskDataService taskDataService;
    private final ConversationMemoryProvisionService provisionService;
    private final ConversationContextAssembler contextAssembler;
    private final MemorySessionPolicyService policyService;
    private final ConversationMemoryProperties properties;

    public ConversationMemoryBridge(ConversationMemorySyncTaskDataService taskDataService,
                                    ConversationMemoryProvisionService provisionService,
                                    ConversationContextAssembler contextAssembler,
                                    MemorySessionPolicyService policyService,
                                    ConversationMemoryProperties properties) {
        this.taskDataService = taskDataService;
        this.provisionService = provisionService;
        this.contextAssembler = contextAssembler;
        this.policyService = policyService;
        this.properties = properties;
    }

    public void assembleContext(ConversationRuntimeContext context) {
        contextAssembler.assemble(context);
    }

    /** Creates a no-content task after the authoritative messages and round status are saved. */
    public void enqueueCompletedRound(ConversationRuntimeContext context, AgentConversationOutcome outcome) {
        if (!properties.isEnabled() || !properties.getSync().isShadowWriteEnabled()
                || !properties.isSessionEnabled() || context == null || outcome == null) {
            return;
        }
        String status = outcome.getStatus();
        if (!"SUCCESS".equalsIgnoreCase(status) && !"INPUT_REQUIRED".equalsIgnoreCase(status)) {
            return;
        }
        ConversationRoundDTO round = context.getRound();
        if (round == null || context.getSession() == null || !StringUtils.hasText(context.getTenantId())) {
            return;
        }
        try {
            ConversationMemorySyncTaskEntity task = new ConversationMemorySyncTaskEntity();
            task.setTaskCode("memory-task-" + UUID.randomUUID().toString().replace("-", ""));
            task.setTenantId(context.getTenantId());
            task.setUserId(context.getSession().getUserId());
            task.setSessionCode(context.getSession().getSessionCode());
            task.setRoundCode(round.getRoundCode());
            task.setTargetScope("SESSION");
            ConversationMemoryBindingEntity active = provisionService.findActive(
                    context.getTenantId(), context.getSession().getUserId());
            task.setTargetMemoryId(active == null ? null : active.getSessionMemoryId());
            task.setOperation("ADD_ROUND");
            task.setSourceVersion(round.getVersion() == null ? 1L : round.getVersion());
            task.setIdempotencyKey(idempotencyKey(task));
            task.setStatus("PENDING");
            task.setRetryCount(0);
            taskDataService.insertIfAbsent(task);
        } catch (RuntimeException ex) {
            // Memory is an optional derived-data path; task persistence must not fail the completed chat round.
            log.warn("Memory同步任务创建失败，sessionCode={}, roundCode={}, errorCode={}",
                    context.getSession().getSessionCode(), round.getRoundCode(), ex.getClass().getSimpleName());
        }
    }

    /**
     * Queues a user-confirmed promotion using Provider locators only. The Worker reads the source
     * text from RAGFlow at execution time, so the Java task table never becomes a Memory store.
     */
    public ConversationMemorySyncTaskEntity enqueuePromotion(String tenantId,
                                                              Long userId,
                                                              String sourceSessionCode,
                                                              String sourceMemoryId,
                                                              String sourceMessageId) {
        if (!properties.isEnabled() || !properties.isLongTermEnabled()
                || !StringUtils.hasText(sourceSessionCode)
                || !StringUtils.hasText(sourceMemoryId)
                || !StringUtils.hasText(sourceMessageId)) {
            throw new IllegalStateException("Memory promotion source is unavailable");
        }
        ConversationMemoryBindingEntity binding = provisionService.findActive(tenantId, userId);
        if (binding == null || !sourceMemoryId.equals(binding.getSessionMemoryId())
                || !StringUtils.hasText(binding.getLongTermMemoryId())) {
            throw new IllegalStateException("Memory promotion binding is unavailable");
        }
        ConversationMemorySyncTaskEntity task = new ConversationMemorySyncTaskEntity();
        task.setTaskCode("memory-task-" + UUID.randomUUID().toString().replace("-", ""));
        task.setTenantId(tenantId);
        task.setUserId(userId);
        task.setSessionCode(sourceSessionCode);
        task.setTargetScope("LONG_TERM");
        task.setTargetMemoryId(binding.getLongTermMemoryId());
        task.setSourceMemoryId(sourceMemoryId);
        task.setSourceMessageId(sourceMessageId);
        task.setOperation("PROMOTE");
        task.setSourceVersion(1L);
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setIdempotencyKey(idempotencyKey(task));
        return taskDataService.insertIfAbsent(task);
    }

    /**
     * Queues deletion of one session's Provider messages. The shared SESSION Memory itself is
     * intentionally retained because it may contain other sessions' messages.
     */
    public void enqueueSessionDeletion(String tenantId, Long userId, String sessionCode) {
        if (!StringUtils.hasText(tenantId) || userId == null || !StringUtils.hasText(sessionCode)) {
            return;
        }
        String normalizedTenantId = tenantId.trim();
        String normalizedSessionCode = sessionCode.trim();
        try {
            policyService.removeForSession(normalizedTenantId, userId, normalizedSessionCode);
        } catch (RuntimeException ex) {
            log.warn("Memory会话策略清理失败，sessionCode={}, errorCode={}",
                    normalizedSessionCode, ex.getClass().getSimpleName());
        }
        if (!properties.isEnabled() || !properties.isSessionEnabled()) {
            return;
        }
        try {
            ConversationMemoryBindingEntity active = provisionService.findActive(normalizedTenantId, userId);
            if (active == null || !StringUtils.hasText(active.getSessionMemoryId())) {
                return;
            }
            ConversationMemorySyncTaskEntity task = new ConversationMemorySyncTaskEntity();
            task.setTaskCode("memory-task-" + UUID.randomUUID().toString().replace("-", ""));
            task.setTenantId(normalizedTenantId);
            task.setUserId(userId);
            task.setSessionCode(normalizedSessionCode);
            task.setTargetScope("SESSION");
            task.setTargetMemoryId(active.getSessionMemoryId());
            task.setOperation("DELETE_SESSION");
            task.setSourceVersion(1L);
            task.setStatus("PENDING");
            task.setRetryCount(0);
            task.setIdempotencyKey(idempotencyKey(task));
            taskDataService.insertIfAbsent(task);
        } catch (RuntimeException ex) {
            // External memory is derived data; cleanup enqueue failure must not roll back chat deletion.
            log.warn("Memory会话清理任务创建失败，sessionCode={}, errorCode={}",
                    normalizedSessionCode, ex.getClass().getSimpleName());
        }
    }

    /**
     * Queues deletion of a retired long-term Provider resource. The binding keeps the retiring
     * ID until the Worker confirms deletion, so a task lost during pointer rotation can be
     * recreated safely from the same idempotency key.
     */
    public ConversationMemorySyncTaskEntity enqueueLongTermMemoryDeletion(String tenantId,
                                                                            Long userId,
                                                                            String oldMemoryId) {
        if (!properties.isEnabled() || !properties.isLongTermEnabled()
                || !StringUtils.hasText(tenantId) || userId == null || !StringUtils.hasText(oldMemoryId)) {
            throw new IllegalStateException("Long-term Memory cleanup is unavailable");
        }
        ConversationMemorySyncTaskEntity task = new ConversationMemorySyncTaskEntity();
        task.setTaskCode("memory-task-" + UUID.randomUUID().toString().replace("-", ""));
        task.setTenantId(tenantId.trim());
        task.setUserId(userId);
        task.setSessionCode("__long-term-memory__");
        task.setTargetScope("LONG_TERM");
        task.setTargetMemoryId(oldMemoryId.trim());
        task.setOperation("DELETE_MEMORY");
        task.setSourceVersion(1L);
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setIdempotencyKey(idempotencyKey(task));
        return taskDataService.insertIfAbsent(task);
    }

    private String idempotencyKey(ConversationMemorySyncTaskEntity task) {
        String source = String.join("\u001f",
                text(task.getTenantId()), String.valueOf(task.getUserId()), text(task.getSessionCode()),
                text(task.getRoundCode()), text(task.getTargetScope()), text(task.getOperation()),
                String.valueOf(task.getSourceVersion()), text(task.getSourceMemoryId()),
                text(task.getSourceMessageId()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
