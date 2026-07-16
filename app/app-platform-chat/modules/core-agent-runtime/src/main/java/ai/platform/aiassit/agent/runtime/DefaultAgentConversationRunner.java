package ai.platform.aiassit.agent.runtime;

import ai.platform.aiassit.model.entity.dto.AiModelConfigDTO;
import ai.platform.aiassit.model.service.AiModelConfigService;
import ai.platform.aiassit.conversation.workflow.artifact.ArtifactAcceptanceResult;
import ai.platform.aiassit.conversation.workflow.artifact.ArtifactAcceptanceService;
import ai.platform.aiassit.conversation.workflow.artifact.ArtifactCheckResult;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.dto.AiEnabledModelDTO;
import ai.platform.aiassit.service.ai.api.dto.ChatMessage;
import ai.platform.aiassit.service.ai.api.enums.MessageRole;
import ai.platform.aiassit.service.ai.spi.agent.AgentCancellation;
import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionSnapshot;
import ai.platform.aiassit.service.ai.spi.agent.AgentEntrySummary;
import ai.platform.aiassit.service.ai.spi.agent.AgentModelConnection;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunAuditRecord;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunAuditStore;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunCommand;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunEvent;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunObserver;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunResult;
import ai.platform.aiassit.service.ai.spi.agent.AgentRuntime;
import ai.platform.aiassit.service.ai.spi.agent.AgentRuntimeType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Default control-plane/run-plane bridge for all formal conversations. */
@Service
@Slf4j
public class DefaultAgentConversationRunner implements AgentConversationRunner {

    private final AgentSnapshotResolver snapshotResolver;
    private final AiModelConfigService modelConfigService;
    private final ArtifactAcceptanceService artifactAcceptanceService;
    private final List<AgentRuntime> runtimes;
    private final List<AgentRunAuditStore> auditStores;
    private final ObjectMapper objectMapper;
    private final AgentCapabilityGrantService capabilityGrantService;

    public DefaultAgentConversationRunner(AgentSnapshotResolver snapshotResolver,
                                          AiModelConfigService modelConfigService,
                                          ArtifactAcceptanceService artifactAcceptanceService,
                                          List<AgentRuntime> runtimes,
                                          List<AgentRunAuditStore> auditStores,
                                          ObjectMapper objectMapper,
                                          AgentCapabilityGrantService capabilityGrantService) {
        this.snapshotResolver = snapshotResolver;
        this.modelConfigService = modelConfigService;
        this.artifactAcceptanceService = artifactAcceptanceService;
        this.runtimes = runtimes == null ? List.of() : List.copyOf(runtimes);
        this.auditStores = auditStores == null ? List.of() : List.copyOf(auditStores);
        this.objectMapper = objectMapper;
        this.capabilityGrantService = capabilityGrantService;
    }

    @Override
    public AgentConversationOutcome run(AgentConversationRequest request,
                                        AgentRunObserver observer,
                                        AgentCancellation cancellation) {
        if (request == null || !StringUtils.hasText(request.getInput())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_MESSAGE);
        }
        AgentTarget target = request.getTarget() == null ? AgentTarget.homeChat() : request.getTarget();
        target = authorizeTarget(target, request.getContext());
        AgentDefinitionSnapshot snapshot = snapshotResolver.resolve(target);
        AgentRuntime runtime = resolveRuntime(snapshot.getRuntimeType());
        AiModelConfigDTO model = resolveModel(request, snapshot);

        String runId = StringUtils.hasText(request.getRunId())
                ? request.getRunId().trim() : "agent-run-" + UUID.randomUUID().toString().replace("-", "");
        AgentRunCommand command = command(request, snapshot, model, runId);
        Instant startedAt = Instant.now();
        AgentRunObserver downstream = observer == null ? AgentRunObserver.NOOP : observer;
        AgentRunObserver enrichingObserver = event -> downstream.onEvent(enrich(event, snapshot, request, runId));
        AgentCancellation signal = cancellation == null ? AgentCancellation.NONE : cancellation;
        try {
            capabilityGrantService.register(runId, request.getUserId(), snapshot,
                    Duration.ofMillis(Math.max(1_000L, command.getTimeoutMs() == null
                            ? 120_000L : command.getTimeoutMs()) + 60_000L));
            AgentRunAuditRecord started = audit(snapshot, request, runId,
                    "RUNNING", startedAt, null, null, null);
            auditStores.forEach(store -> store.create(started));
            AgentRunResult result = runtime.run(snapshot, command, enrichingObserver, signal);
            AgentRunResult settled = result == null ? new AgentRunResult() : result;
            if (!StringUtils.hasText(settled.getRunId())) {
                settled.setRunId(runId);
            }
            ArtifactAcceptanceResult acceptance = accept(snapshot, settled, enrichingObserver, runId, request);
            int repairAttempt = 0;
            while (!acceptance.isAccepted() && acceptance.isRepairable()
                    && repairAttempt < acceptance.getMaxRepairAttempts()) {
                repairAttempt++;
                emitRepair(enrichingObserver, snapshot, request, runId, repairAttempt, acceptance);
                appendRepairTurn(command, settled, acceptance.getRepairMessage());
                AgentRunResult repaired = runtime.run(snapshot, command, enrichingObserver, signal);
                settled = repaired == null ? new AgentRunResult() : repaired;
                settled.setRunId(runId);
                acceptance = accept(snapshot, settled, enrichingObserver, runId, request);
            }
            String status;
            if (acceptance.isAccepted()) {
                status = "SUCCESS";
            } else if (acceptance.isInputRequired()) {
                status = "INPUT_REQUIRED";
                settled.setFinalOutput(acceptance.getRepairMessage());
            } else {
                throw BizException.of(AiChatBizCodeConstant.WORKFLOW_EXECUTION_FAILED,
                        acceptance.getRepairMessage());
            }
            settled.setStatus(status);
            settled.setArtifacts(acceptance.getArtifacts());
            AgentRunAuditRecord finished = audit(snapshot, request, runId, status, startedAt, Instant.now(),
                    usageJson(settled), null);
            auditStores.forEach(store -> store.update(finished));
            return outcome(snapshot, model, settled, status);
        } catch (RuntimeException ex) {
            String failureStatus = signal.isCancellationRequested() || Thread.currentThread().isInterrupted()
                    ? "CANCELLED" : "FAILED";
            AgentRunAuditRecord failed = audit(snapshot, request, runId, failureStatus, startedAt, Instant.now(),
                    null, safeError(ex));
            auditStores.forEach(store -> store.update(failed));
            throw ex;
        } finally {
            capabilityGrantService.revoke(runId);
        }
    }

    private ArtifactAcceptanceResult accept(AgentDefinitionSnapshot snapshot,
                                            AgentRunResult result,
                                            AgentRunObserver observer,
                                            String runId,
                                            AgentConversationRequest request) {
        ArtifactAcceptanceResult acceptance = artifactAcceptanceService.accept(
                snapshot.getWorkflowSnapshot(),
                result == null ? List.of() : result.getArtifacts(),
                result == null ? null : result.getFinalOutput());
        for (ArtifactCheckResult check : acceptance.getChecks()) {
            AgentRunEvent started = checkEvent("check.started", check, runId, snapshot, request, "RUNNING");
            observer.onEvent(started);
            AgentRunEvent completed = checkEvent("check.completed", check, runId, snapshot, request,
                    check.isPassed() ? check.getStatus() : "FAILED");
            observer.onEvent(completed);
        }
        return acceptance;
    }

    private AgentRunEvent checkEvent(String eventType,
                                     ArtifactCheckResult check,
                                     String runId,
                                     AgentDefinitionSnapshot snapshot,
                                     AgentConversationRequest request,
                                     String status) {
        AgentRunEvent event = new AgentRunEvent();
        event.setEventType(eventType);
        event.setRunId(runId);
        event.setRequestId(request.getRequestId());
        event.setTraceId(request.getTraceId());
        event.setSessionCode(request.getSessionCode());
        event.setRoundCode(request.getRoundCode());
        event.setAgentCode(snapshot.getAgentCode());
        event.setAgentVersion(snapshot.getAgentVersion());
        event.setStatus(status);
        event.setMessage(check.getMessage());
        event.setExt(Map.of(
                "checkCode", check.getCheckCode(),
                "targetArtifact", check.getTargetArtifact() == null ? "" : check.getTargetArtifact(),
                "checkerType", check.getCheckerType(),
                "severity", check.getSeverity(),
                "blocking", check.isBlocking(),
                "retryable", check.isRetryable(),
                "passed", check.isPassed()
        ));
        return event;
    }

    private void emitRepair(AgentRunObserver observer,
                            AgentDefinitionSnapshot snapshot,
                            AgentConversationRequest request,
                            String runId,
                            int attempt,
                            ArtifactAcceptanceResult acceptance) {
        AgentRunEvent event = new AgentRunEvent();
        event.setEventType("artifact.repair.requested");
        event.setRunId(runId);
        event.setRequestId(request.getRequestId());
        event.setTraceId(request.getTraceId());
        event.setSessionCode(request.getSessionCode());
        event.setRoundCode(request.getRoundCode());
        event.setAgentCode(snapshot.getAgentCode());
        event.setAgentVersion(snapshot.getAgentVersion());
        event.setStatus("RUNNING");
        event.setMessage(acceptance.getRepairMessage());
        event.setExt(Map.of("repairAttempt", attempt, "maxRepairAttempts", acceptance.getMaxRepairAttempts()));
        observer.onEvent(event);
    }

    private void appendRepairTurn(AgentRunCommand command, AgentRunResult previous, String repairMessage) {
        List<ChatMessage> messages = new java.util.ArrayList<>(command.getMessages());
        if (previous != null && StringUtils.hasText(previous.getFinalOutput())) {
            ChatMessage assistant = new ChatMessage();
            assistant.setRole(MessageRole.ASSISTANT);
            assistant.setContent(previous.getFinalOutput());
            messages.add(assistant);
        }
        ChatMessage repair = new ChatMessage();
        repair.setRole(MessageRole.USER);
        repair.setContent(repairMessage);
        messages.add(repair);
        command.setMessages(messages);
        command.setInput(repairMessage);
    }

    @Override
    public List<AgentEntrySummary> availableAgents(String entryCode) {
        return snapshotResolver.available(StringUtils.hasText(entryCode) ? entryCode.trim() : "HOME_CHAT");
    }

    AgentTarget authorizeTarget(AgentTarget target, Map<String, Object> context) {
        if (target == null || !target.explicit()) {
            return target == null ? AgentTarget.homeChat() : target;
        }
        boolean privileged = context != null && Boolean.TRUE.equals(context.get("allowExplicitAgent"));
        if (privileged) {
            return target;
        }
        AgentEntrySummary allowed = snapshotResolver.available("HOME_CHAT").stream()
                .filter(item -> target.agentCode().equals(item.getCode()))
                .filter(item -> target.agentVersion() == null || target.agentVersion().equals(item.getVersion()))
                .findFirst()
                .orElseThrow(() -> BizException.of(AiChatBizCodeConstant.AGENT_EXECUTION_FAILED,
                        "Agent is not available for HOME_CHAT entry"));
        // An omitted version must not escape the entry binding and resolve a newer, globally published version.
        return AgentTarget.explicit(allowed.getCode(), allowed.getVersion());
    }

    private AgentRunCommand command(AgentConversationRequest request,
                                    AgentDefinitionSnapshot snapshot,
                                    AiModelConfigDTO model,
                                    String runId) {
        AgentRunCommand command = new AgentRunCommand();
        command.setRunId(runId);
        command.setRequestId(request.getRequestId());
        command.setTraceId(request.getTraceId());
        command.setSessionCode(request.getSessionCode());
        command.setRoundCode(request.getRoundCode());
        command.setUserId(request.getUserId());
        command.setInput(request.getInput());
        command.setMessages(request.getMessages());
        command.setContext(request.getContext());
        command.setMaxTurns(intAt(snapshot.getRootAgent(), 12, "spec", "runtimeDefaults", "maxTurns"));
        command.setTimeoutMs(intAt(snapshot.getRootAgent(), 120_000, "spec", "runtimeDefaults", "timeoutMs"));

        AgentModelConnection connection = new AgentModelConnection();
        connection.setModelCode(model.getModelCode());
        connection.setModel(model.getApiModel());
        connection.setBaseUrl(model.getBaseUrl());
        connection.setApiKey(model.getApiKey());
        connection.setSettings(mapAt(snapshot.getRootAgent(), "spec", "model", "settings"));
        command.setModelConnection(connection);
        return command;
    }

    private AiModelConfigDTO resolveModel(AgentConversationRequest request, AgentDefinitionSnapshot snapshot) {
        if (request.getModelOverrideId() != null
                && (request.getContext() == null
                || !Boolean.TRUE.equals(request.getContext().get("allowModelOverride")))) {
            throw BizException.of(AiChatBizCodeConstant.AGENT_EXECUTION_FAILED,
                    "Model override is not permitted for this Agent conversation");
        }
        AiModelConfigDTO model = request.getModelOverrideId() == null
                ? null : modelConfigService.getResolvedById(request.getModelOverrideId());
        if (model == null) {
            String ref = textAt(snapshot.getRootAgent(), "spec", "model", "ref");
            if (StringUtils.hasText(ref) && ref.startsWith("model://") && !"model://default-quality".equals(ref)) {
                model = modelConfigService.getByModelCode(ref.substring("model://".length()));
            }
        }
        if (model == null) {
            List<AiEnabledModelDTO> enabled = modelConfigService.selectEnabledModels();
            if (enabled != null && !enabled.isEmpty() && enabled.get(0).getId() != null) {
                model = modelConfigService.getResolvedById(enabled.get(0).getId());
            }
        }
        if (model == null || !Boolean.TRUE.equals(model.getEnabled())
                || !StringUtils.hasText(model.getApiModel()) || !StringUtils.hasText(model.getBaseUrl())) {
            throw BizException.of(AiChatBizCodeConstant.MODEL_CONFIG_NOT_FOUND,
                    textAt(snapshot.getRootAgent(), "spec", "model", "ref"));
        }
        return model;
    }

    private AgentRuntime resolveRuntime(AgentRuntimeType runtimeType) {
        return runtimes.stream()
                .filter(runtime -> runtime.capabilities() != null)
                .filter(runtime -> runtime.capabilities().getRuntimeType() == runtimeType)
                .findFirst()
                .orElseThrow(() -> BizException.of(AiChatBizCodeConstant.AI_CHAT_SERVICE_NOT_FOUND, runtimeType));
    }

    private AgentRunEvent enrich(AgentRunEvent event,
                                 AgentDefinitionSnapshot snapshot,
                                 AgentConversationRequest request,
                                 String runId) {
        AgentRunEvent value = event == null ? new AgentRunEvent() : event;
        value.setRunId(runId);
        if (!StringUtils.hasText(value.getRequestId())) {
            value.setRequestId(request.getRequestId());
        }
        if (!StringUtils.hasText(value.getTraceId())) {
            value.setTraceId(request.getTraceId());
        }
        if (!StringUtils.hasText(value.getSessionCode())) {
            value.setSessionCode(request.getSessionCode());
        }
        if (!StringUtils.hasText(value.getRoundCode())) {
            value.setRoundCode(request.getRoundCode());
        }
        if (!StringUtils.hasText(value.getAgentCode())) {
            value.setAgentCode(snapshot.getAgentCode());
        }
        if (value.getAgentVersion() == null && snapshot.getAgentCode().equals(value.getAgentCode())) {
            value.setAgentVersion(snapshot.getAgentVersion());
        }
        return value;
    }

    private AgentConversationOutcome outcome(AgentDefinitionSnapshot snapshot,
                                             AiModelConfigDTO model,
                                             AgentRunResult result,
                                             String status) {
        AgentConversationOutcome outcome = new AgentConversationOutcome();
        outcome.setRunId(result.getRunId());
        outcome.setAnswer(result.getFinalOutput());
        outcome.setRootAgentCode(snapshot.getAgentCode());
        outcome.setRootAgentVersion(snapshot.getAgentVersion());
        outcome.setRuntimeType(snapshot.getRuntimeType());
        outcome.setSdkVersion(snapshot.getSdkVersion());
        outcome.setSnapshotHash(snapshot.getSnapshotHash());
        outcome.setModelCode(model.getModelCode());
        outcome.setActualModel(model.getApiModel());
        outcome.setStatus(status);
        outcome.setUsage(result.getUsage());
        outcome.setArtifacts(result.getArtifacts());
        return outcome;
    }

    private AgentRunAuditRecord audit(AgentDefinitionSnapshot snapshot,
                                      AgentConversationRequest request,
                                      String runId,
                                      String status,
                                      Instant startedAt,
                                      Instant finishedAt,
                                      String usage,
                                      String error) {
        String workflowRef = String.valueOf(snapshot.getWorkflowSnapshot().getOrDefault("workflowRef", ""));
        WorkflowRef workflow = WorkflowRef.parse(workflowRef);
        return AgentRunAuditRecord.builder()
                .runId(runId)
                .sessionCode(request.getSessionCode())
                .roundCode(request.getRoundCode())
                .rootAgentCode(snapshot.getAgentCode())
                .rootAgentVersion(snapshot.getAgentVersion())
                .workflowCode(workflow.code())
                .workflowVersion(workflow.version())
                .runtimeType(snapshot.getRuntimeType())
                .sdkVersion(snapshot.getSdkVersion())
                .snapshotHash(snapshot.getSnapshotHash())
                .traceId(request.getTraceId())
                .status(status)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .usageJson(usage)
                .errorSummary(error)
                .build();
    }

    private String usageJson(AgentRunResult result) {
        try {
            return objectMapper.writeValueAsString(result.getUsage());
        } catch (JsonProcessingException ex) {
            log.warn("Agent usage serialization failed, runId={}", result.getRunId());
            return "{}";
        }
    }

    private String safeError(RuntimeException ex) {
        String message = ex == null ? null : ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return ex == null ? "Agent run failed" : ex.getClass().getSimpleName();
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapAt(Map<String, Object> root, String... path) {
        Object current = root;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return Map.of();
            }
            current = map.get(key);
        }
        return current instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String textAt(Map<String, Object> root, String... path) {
        Object current = root;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
        }
        return current instanceof String text && StringUtils.hasText(text) ? text.trim() : null;
    }

    private int intAt(Map<String, Object> root, int fallback, String... path) {
        Object current = root;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return fallback;
            }
            current = map.get(key);
        }
        return current instanceof Number number ? number.intValue() : fallback;
    }

    private record WorkflowRef(String code, Integer version) {
        private static WorkflowRef parse(String ref) {
            if (!StringUtils.hasText(ref) || !ref.startsWith("workflow://")) {
                return new WorkflowRef(null, null);
            }
            String[] segments = ref.substring("workflow://".length()).split("/v", 2);
            Integer version = null;
            if (segments.length == 2) {
                try {
                    version = Integer.valueOf(segments[1]);
                } catch (NumberFormatException ignored) {
                    version = null;
                }
            }
            return new WorkflowRef(segments[0], version);
        }
    }
}
