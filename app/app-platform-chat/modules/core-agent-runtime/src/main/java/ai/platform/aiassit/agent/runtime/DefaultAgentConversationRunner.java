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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Conversation/run-plane bridge.
 *
 * <p>Agent prompts, tools, skills and collaboration are deliberately owned by
 * the Python worker. Java only assembles a conversation command, resolves its
 * model connection and relays the worker's stream.</p>
 */
@Service
@Slf4j
public class DefaultAgentConversationRunner implements AgentConversationRunner {

    private final AiModelConfigService modelConfigService;
    private final ArtifactAcceptanceService artifactAcceptanceService;
    private final List<AgentRuntime> runtimes;
    private final List<AgentRunAuditStore> auditStores;
    private final ObjectMapper objectMapper;

    public DefaultAgentConversationRunner(AiModelConfigService modelConfigService,
                                          ArtifactAcceptanceService artifactAcceptanceService,
                                          List<AgentRuntime> runtimes,
                                          List<AgentRunAuditStore> auditStores,
                                          ObjectMapper objectMapper) {
        this.modelConfigService = modelConfigService;
        this.artifactAcceptanceService = artifactAcceptanceService;
        this.runtimes = runtimes == null ? List.of() : List.copyOf(runtimes);
        this.auditStores = auditStores == null ? List.of() : List.copyOf(auditStores);
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentConversationOutcome run(AgentConversationRequest request,
                                        AgentRunObserver observer,
                                        AgentCancellation cancellation) {
        if (request == null || !StringUtils.hasText(request.getInput())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_MESSAGE);
        }
        AgentDefinitionSnapshot snapshot = pythonRuntimeSnapshot();
        AgentRuntime runtime = resolveRuntime(AgentRuntimeType.OPENAI_AGENTS_PYTHON);
        AiModelConfigDTO model = resolveModel(request);

        String runId = StringUtils.hasText(request.getRunId())
                ? request.getRunId().trim() : "agent-run-" + UUID.randomUUID().toString().replace("-", "");
        AgentRunCommand command = command(request, model, runId);
        Instant startedAt = Instant.now();
        AgentRunObserver downstream = observer == null ? AgentRunObserver.NOOP : observer;
        AgentRunObserver enrichingObserver = event -> downstream.onEvent(enrich(event, snapshot, request, runId));
        AgentCancellation signal = cancellation == null ? AgentCancellation.NONE : cancellation;
        try {
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
        // Python owns the entry catalog. This method is retained only for the
        // existing SPI and must not recreate a Java-side Agent definition.
        return List.of();
    }

    private AgentRunCommand command(AgentConversationRequest request,
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
        Map<String, Object> context = new LinkedHashMap<>();
        if (request.getContext() != null) {
            context.putAll(request.getContext());
        }
        AgentTarget target = request.getTarget() == null ? AgentTarget.homeChat() : request.getTarget();
        context.put("agentEntry", target.explicit() ? target.agentCode() : target.entryCode());
        command.setContext(context);
        command.setMaxTurns(12);
        command.setTimeoutMs(120_000);

        AgentModelConnection connection = new AgentModelConnection();
        connection.setModelCode(model.getModelCode());
        connection.setModel(model.getApiModel());
        connection.setBaseUrl(model.getBaseUrl());
        connection.setApiKey(model.getApiKey());
        connection.setSettings(Map.of());
        command.setModelConnection(connection);
        return command;
    }

    AiModelConfigDTO resolveModel(AgentConversationRequest request) {
        AiModelConfigDTO model = null;
        if (request.getModelId() != null) {
            model = modelConfigService.getResolvedById(request.getModelId());
            if (model == null) {
                throw BizException.of(AiChatBizCodeConstant.MODEL_CONFIG_NOT_FOUND, request.getModelId());
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
                    request.getModelId());
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

    private AgentDefinitionSnapshot pythonRuntimeSnapshot() {
        AgentDefinitionSnapshot snapshot = new AgentDefinitionSnapshot();
        snapshot.setAgentCode("python-agent-runtime");
        snapshot.setAgentVersion(1);
        snapshot.setRuntimeType(AgentRuntimeType.OPENAI_AGENTS_PYTHON);
        snapshot.setSdkVersion("python-local");
        snapshot.setSnapshotHash("python-local");
        return snapshot;
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
