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
import java.util.ArrayList;
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
        AgentDefinitionSnapshot snapshot = pythonRuntimeSnapshot(request);
        AgentRuntime runtime = resolveRuntime(AgentRuntimeType.OPENAI_AGENTS_PYTHON);
        AiModelConfigDTO model = resolveModel(request);

        String runId = StringUtils.hasText(request.getRunId())
                ? request.getRunId().trim() : "agent-run-" + UUID.randomUUID().toString().replace("-", "");
        AgentRunCommand command = command(request, model, runId);
        Instant startedAt = Instant.now();
        AgentRunObserver downstream = observer == null ? AgentRunObserver.NOOP : observer;
        AgentRunObserver enrichingObserver = event -> downstream.onEvent(enrich(event, snapshot, request, runId));
        AgentCancellation signal = cancellation == null ? AgentCancellation.NONE : cancellation;
        ArtifactAcceptanceResult acceptance = null;
        AgentRunResult settled = null;
        int repairAttempt = 0;
        int acceptanceAttempt = 0;
        boolean resultEventEmitted = false;
        try {
            AgentRunAuditRecord started = audit(snapshot, request, runId,
                    "RUNNING", startedAt, null, null, null);
            auditStores.forEach(store -> store.create(started));
            setExecutionAttempt(command, 1);
            AgentRunResult result = runtime.run(snapshot, command, enrichingObserver, signal);
            settled = result == null ? new AgentRunResult() : result;
            if (!StringUtils.hasText(settled.getRunId())) {
                settled.setRunId(runId);
            }
            reconcileArtifactDelivery(snapshot, settled);
            acceptanceAttempt++;
            acceptance = accept(snapshot, settled, enrichingObserver, runId, request, acceptanceAttempt);
            while (!acceptance.isAccepted() && acceptance.isRepairable()
                    && repairAttempt < acceptance.getMaxRepairAttempts()) {
                repairAttempt++;
                emitRepairRequested(enrichingObserver, snapshot, request, runId, repairAttempt, acceptance);
                appendRepairTurn(command, settled, acceptance.getRepairMessage());
                setExecutionAttempt(command, repairAttempt + 1);
                AgentRunResult repaired;
                try {
                    repaired = runtime.run(snapshot, command, enrichingObserver, signal);
                } catch (RuntimeException repairError) {
                    emitRepairFailed(enrichingObserver, snapshot, request, runId,
                            repairAttempt, acceptance, repairError);
                    throw repairError;
                }
                settled = repaired == null ? new AgentRunResult() : repaired;
                settled.setRunId(runId);
                reconcileArtifactDelivery(snapshot, settled);
                emitRepairCompleted(enrichingObserver, snapshot, request, runId, repairAttempt,
                        acceptance, settled);
                acceptanceAttempt++;
                acceptance = accept(snapshot, settled, enrichingObserver, runId, request, acceptanceAttempt);
            }
            String status;
            if (acceptance.isAccepted()) {
                status = runtimeInputRequired(settled) ? "INPUT_REQUIRED" : "SUCCESS";
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
            String resultStatus = executionResultStatus(acceptance, settled);
            resultEventEmitted = true;
            emitExecutionResult(enrichingObserver, snapshot, request, runId, resultStatus,
                    settled, acceptance, repairAttempt, acceptanceAttempt, null);
            return outcome(snapshot, model, settled, status);
        } catch (RuntimeException ex) {
            String failureStatus = signal.isCancellationRequested() || Thread.currentThread().isInterrupted()
                    ? "CANCELLED" : "FAILED";
            if (!resultEventEmitted) {
                resultEventEmitted = true;
                try {
                    emitExecutionResult(enrichingObserver, snapshot, request, runId, failureStatus,
                            settled, acceptance, repairAttempt, acceptanceAttempt, ex);
                } catch (RuntimeException emitError) {
                    log.warn("Agent execution result event emission failed, runId={}", runId, emitError);
                }
            }
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
                                            AgentConversationRequest request,
                                            int acceptanceAttempt) {
        ArtifactAcceptanceResult acceptance = artifactAcceptanceService.accept(
                snapshot.getWorkflowSnapshot(),
                result == null ? List.of() : result.getArtifacts(),
                result == null ? null : result.getFinalOutput());
        List<ArtifactCheckResult> checks = checks(acceptance);
        for (int index = 0; index < checks.size(); index++) {
            ArtifactCheckResult check = checks.get(index);
            String activityCode = checkActivityCode(check, acceptanceAttempt, index + 1);
            AgentRunEvent started = checkEvent("check.started", check, runId, snapshot, request,
                    "RUNNING", activityCode, acceptanceAttempt);
            observer.onEvent(started);
            AgentRunEvent completed = checkEvent("check.completed", check, runId, snapshot, request,
                    checkActivityStatus(check), activityCode, acceptanceAttempt);
            observer.onEvent(completed);
        }
        return acceptance;
    }

    private AgentRunEvent checkEvent(String eventType,
                                     ArtifactCheckResult check,
                                     String runId,
                                     AgentDefinitionSnapshot snapshot,
                                     AgentConversationRequest request,
                                     String status,
                                     String activityCode,
                                     int acceptanceAttempt) {
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
        boolean completed = eventType.endsWith(".completed");
        String checkCode = textOrDefault(check.getCheckCode(), "artifact-check");
        event.setMessage(completed
                ? textOrDefault(check.getMessage(), "产物验收检查已完成：" + checkCode)
                : "开始执行产物验收检查：" + checkCode);
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("activityCode", activityCode);
        ext.put("activityType", "ACCEPTANCE_CHECK");
        ext.put("activityName", "产物验收检查：" + checkCode);
        ext.put("attempt", acceptanceAttempt);
        ext.put("repairAttempt", Math.max(0, acceptanceAttempt - 1));
        ext.put("checkCode", checkCode);
        ext.put("targetArtifact", textOrDefault(check.getTargetArtifact(), ""));
        ext.put("checkerType", textOrDefault(check.getCheckerType(), "UNKNOWN"));
        ext.put("severity", textOrDefault(check.getSeverity(), "UNKNOWN"));
        ext.put("blocking", check.isBlocking());
        ext.put("retryable", check.isRetryable());
        ext.put("passed", check.isPassed());
        ext.put("checkStatus", checkStatus(check));
        ext.put("inputSummary", checkInputSummary(check));
        if (completed) {
            ext.put("outputSummary", textOrDefault(check.getMessage(), checkStatus(check)));
        }
        event.setExt(ext);
        return event;
    }

    private void emitRepairRequested(AgentRunObserver observer,
                                     AgentDefinitionSnapshot snapshot,
                                     AgentConversationRequest request,
                                     String runId,
                                     int attempt,
                                     ArtifactAcceptanceResult acceptance) {
        AgentRunEvent event = repairEvent("artifact.repair.requested", "RUNNING", snapshot, request,
                runId, attempt, acceptance);
        event.setMessage(textOrDefault(acceptance.getRepairMessage(), "开始第 " + attempt + " 次产物修复。"));
        event.getExt().put("inputSummary", summarize(acceptance.getRepairMessage(), "修复未通过的产物验收项。"));
        observer.onEvent(event);
    }

    private void emitRepairCompleted(AgentRunObserver observer,
                                     AgentDefinitionSnapshot snapshot,
                                     AgentConversationRequest request,
                                     String runId,
                                     int attempt,
                                     ArtifactAcceptanceResult acceptance,
                                     AgentRunResult repaired) {
        AgentRunEvent event = repairEvent("artifact.repair.completed", "SUCCESS", snapshot, request,
                runId, attempt, acceptance);
        String outputSummary = "第 " + attempt + " 次产物修复执行完成，等待重新验收。";
        event.setMessage(outputSummary);
        event.getExt().put("artifactCount", runtimeArtifacts(repaired).size());
        event.getExt().put("outputSummary", outputSummary);
        observer.onEvent(event);
    }

    private void emitRepairFailed(AgentRunObserver observer,
                                  AgentDefinitionSnapshot snapshot,
                                  AgentConversationRequest request,
                                  String runId,
                                  int attempt,
                                  ArtifactAcceptanceResult acceptance,
                                  RuntimeException failure) {
        AgentRunEvent event = repairEvent("artifact.repair.failed", "FAILED", snapshot, request,
                runId, attempt, acceptance);
        String outputSummary = "第 " + attempt + " 次产物修复执行失败，已停止本次自动补救。";
        event.setMessage(outputSummary);
        event.getExt().put("failureType", failure == null
                ? "RuntimeException" : failure.getClass().getSimpleName());
        event.getExt().put("outputSummary", outputSummary);
        observer.onEvent(event);
    }

    private AgentRunEvent repairEvent(String eventType,
                                      String status,
                                      AgentDefinitionSnapshot snapshot,
                                      AgentConversationRequest request,
                                      String runId,
                                      int attempt,
                                      ArtifactAcceptanceResult acceptance) {
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
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("activityCode", "artifact-repair:attempt:" + attempt);
        ext.put("activityType", "ARTIFACT_REPAIR");
        ext.put("activityName", "产物修复（第 " + attempt + " 次）");
        ext.put("attempt", attempt);
        ext.put("repairAttempt", attempt);
        ext.put("maxRepairAttempts", acceptance == null ? 0 : acceptance.getMaxRepairAttempts());
        event.setExt(ext);
        return event;
    }

    private void emitExecutionResult(AgentRunObserver observer,
                                     AgentDefinitionSnapshot snapshot,
                                     AgentConversationRequest request,
                                     String runId,
                                     String resultStatus,
                                     AgentRunResult result,
                                     ArtifactAcceptanceResult acceptance,
                                     int repairAttempts,
                                     int acceptanceAttempts,
                                     RuntimeException failure) {
        List<ArtifactCheckResult> checks = checks(acceptance);
        Map<String, Object> checkCounts = checkCounts(checks);
        List<Map<String, Object>> remainingIssues = remainingIssues(checks);
        int artifactCount = acceptedArtifacts(result, acceptance).size();
        if ("FAILED".equals(resultStatus) && remainingIssues.isEmpty()) {
            remainingIssues.add(executionFailureIssue());
        }

        String outputSummary = executionOutputSummary(resultStatus, acceptance, checkCounts, artifactCount);
        AgentRunEvent event = new AgentRunEvent();
        event.setEventType("execution.result.completed");
        event.setRunId(runId);
        event.setRequestId(request.getRequestId());
        event.setTraceId(request.getTraceId());
        event.setSessionCode(request.getSessionCode());
        event.setRoundCode(request.getRoundCode());
        event.setAgentCode(snapshot.getAgentCode());
        event.setAgentVersion(snapshot.getAgentVersion());
        event.setStatus(resultStatus);
        event.setMessage(outputSummary);

        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("activityCode", "execution-result");
        ext.put("activityType", "EXECUTION_RESULT");
        ext.put("activityName", "执行结果");
        ext.put("attempt", acceptanceAttempts);
        ext.put("authoritative", true);
        ext.put("resultStatus", resultStatus);
        ext.put("outcomeStatus", resultStatus);
        ext.put("accepted", acceptance != null && acceptance.isAccepted());
        ext.put("artifactCount", artifactCount);
        ext.put("repairAttempts", repairAttempts);
        ext.put("remediationAttempts", repairAttempts);
        ext.put("maxRepairAttempts", acceptance == null ? 0 : acceptance.getMaxRepairAttempts());
        ext.put("checks", checkCounts);
        ext.put("checksPassed", passedCheckCodes(checks));
        ext.put("checksFailed", failedCheckCodes(checks));
        ext.put("completionCoverage", completionCoverage(resultStatus, checkCounts));
        ext.put("remainingIssues", remainingIssues);
        ext.put("nextAction", nextAction(resultStatus, acceptance));
        ext.put("inputSummary", summarize(request.getInput(), "用户未提供可摘要的输入。"));
        ext.put("outputSummary", outputSummary);
        ext.put("resultSummary", summarize(result == null ? null : result.getFinalOutput(),
                "本轮未形成可展示的最终结果。"));
        Double answerConfidence = answerConfidence(result);
        if (answerConfidence != null) {
            ext.put("answerConfidence", answerConfidence);
        }
        if (failure != null) {
            ext.put("failureType", failure.getClass().getSimpleName());
        }
        event.setExt(ext);
        observer.onEvent(event);
    }

    private String executionResultStatus(ArtifactAcceptanceResult acceptance,
                                         AgentRunResult result) {
        if (acceptance == null) {
            return "FAILED";
        }
        if (acceptance.isAccepted()) {
            if (runtimeInputRequired(result)) {
                return "INPUT_REQUIRED";
            }
            return checks(acceptance).stream().anyMatch(check -> !check.isPassed())
                    ? "PARTIAL" : "SUCCESS";
        }
        return acceptance.isInputRequired() ? "INPUT_REQUIRED" : "FAILED";
    }

    private Map<String, Object> checkCounts(List<ArtifactCheckResult> checks) {
        int passed = 0;
        int failed = 0;
        int blockingFailed = 0;
        for (ArtifactCheckResult check : checks) {
            if (check.isPassed()) {
                passed++;
            } else {
                failed++;
                if (check.isBlocking()) {
                    blockingFailed++;
                }
            }
        }
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("total", checks.size());
        counts.put("passed", passed);
        counts.put("failed", failed);
        counts.put("blockingFailed", blockingFailed);
        return counts;
    }

    private List<String> passedCheckCodes(List<ArtifactCheckResult> checks) {
        return checks.stream()
                .filter(ArtifactCheckResult::isPassed)
                .map(check -> textOrDefault(check.getCheckCode(), "artifact-check"))
                .toList();
    }

    private List<String> failedCheckCodes(List<ArtifactCheckResult> checks) {
        return checks.stream()
                .filter(check -> !check.isPassed())
                .map(check -> textOrDefault(check.getCheckCode(), "artifact-check"))
                .toList();
    }

    private double completionCoverage(String resultStatus, Map<String, Object> checkCounts) {
        int total = (int) checkCounts.get("total");
        int passed = (int) checkCounts.get("passed");
        if (total > 0) {
            return Math.round((passed * 1000.0d) / total) / 1000.0d;
        }
        return "SUCCESS".equals(resultStatus) ? 1.0d : 0.0d;
    }

    @SuppressWarnings("unchecked")
    private Double answerConfidence(AgentRunResult result) {
        if (result == null || result.getProviderMeta() == null
                || !(result.getProviderMeta().get("confidence") instanceof Map<?, ?> raw)) {
            return null;
        }
        Object value = ((Map<String, Object>) raw).get("confidence");
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private List<Map<String, Object>> remainingIssues(List<ArtifactCheckResult> checks) {
        List<Map<String, Object>> issues = new ArrayList<>();
        for (ArtifactCheckResult check : checks) {
            if (check.isPassed()) {
                continue;
            }
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("checkCode", textOrDefault(check.getCheckCode(), "artifact-check"));
            issue.put("targetArtifact", textOrDefault(check.getTargetArtifact(), ""));
            issue.put("checkerType", textOrDefault(check.getCheckerType(), "UNKNOWN"));
            issue.put("severity", textOrDefault(check.getSeverity(), "UNKNOWN"));
            issue.put("blocking", check.isBlocking());
            issue.put("retryable", check.isRetryable());
            issue.put("status", checkStatus(check));
            issue.put("message", textOrDefault(check.getMessage(), "产物验收检查未通过。"));
            issues.add(issue);
        }
        return issues;
    }

    private Map<String, Object> executionFailureIssue() {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("checkCode", "execution");
        issue.put("targetArtifact", "");
        issue.put("checkerType", "RUNTIME");
        issue.put("severity", "ERROR");
        issue.put("blocking", true);
        issue.put("retryable", false);
        issue.put("status", "FAILED");
        issue.put("message", "Agent 执行在产物验收完成前失败，未形成可交付结果。");
        return issue;
    }

    private Map<String, Object> nextAction(String resultStatus, ArtifactAcceptanceResult acceptance) {
        Map<String, Object> action = new LinkedHashMap<>();
        switch (resultStatus) {
            case "SUCCESS" -> {
                action.put("type", "DELIVER_RESULT");
                action.put("description", "向用户交付已通过验收的结果。");
            }
            case "PARTIAL" -> {
                action.put("type", "REVIEW_REMAINING_ISSUES");
                action.put("description", "结果可以交付，但应复核并处理剩余的非阻断问题。");
            }
            case "INPUT_REQUIRED" -> {
                action.put("type", "REQUEST_USER_INPUT");
                action.put("description", summarize(acceptance == null ? null : acceptance.getRepairMessage(),
                        "请用户补充完成任务所需的信息。"));
            }
            case "FAILED" -> {
                action.put("type", "STOP_AND_REPORT_FAILURE");
                action.put("description", summarize(acceptance == null ? null : acceptance.getRepairMessage(),
                        "停止当前执行并报告失败原因。"));
            }
            default -> {
                action.put("type", "NONE");
                action.put("description", "执行已取消，无需继续处理。");
            }
        }
        return action;
    }

    private String executionOutputSummary(String resultStatus,
                                          ArtifactAcceptanceResult acceptance,
                                          Map<String, Object> checkCounts,
                                          int artifactCount) {
        int total = (int) checkCounts.get("total");
        int failed = (int) checkCounts.get("failed");
        return switch (resultStatus) {
            case "SUCCESS" -> "执行结果验收通过：生成 " + artifactCount + " 个产物，"
                    + total + " 项检查全部通过。";
            case "PARTIAL" -> "执行结果可交付：生成 " + artifactCount + " 个产物，仍有 "
                    + failed + " 个非阻断问题需要复核。";
            case "INPUT_REQUIRED" -> "执行结果尚不能交付，需要用户补充信息："
                    + summarize(acceptance == null ? null : acceptance.getRepairMessage(), "缺少必要输入。");
            case "CANCELLED" -> "执行已取消，未继续进行产物验收或交付。";
            default -> "执行失败，未形成可交付结果。";
        };
    }

    private String checkActivityCode(ArtifactCheckResult check, int acceptanceAttempt, int checkIndex) {
        String checkCode = textOrDefault(check == null ? null : check.getCheckCode(), "check-" + checkIndex);
        String prefix = "artifact-check:";
        String suffix = ":attempt:" + acceptanceAttempt;
        int maxCheckCodeLength = Math.max(1, 128 - prefix.length() - suffix.length());
        if (checkCode.length() > maxCheckCodeLength) {
            checkCode = checkCode.substring(0, maxCheckCodeLength);
        }
        return prefix + checkCode + suffix;
    }

    private String checkStatus(ArtifactCheckResult check) {
        if (check == null) {
            return "FAILED";
        }
        return textOrDefault(check.getStatus(), check.isPassed() ? "PASSED" : "FAILED");
    }

    private String checkActivityStatus(ArtifactCheckResult check) {
        return check != null && check.isPassed() ? "SUCCESS" : "FAILED";
    }

    private String checkInputSummary(ArtifactCheckResult check) {
        String target = textOrDefault(check.getTargetArtifact(), "未指定产物");
        String checkerType = textOrDefault(check.getCheckerType(), "UNKNOWN");
        return "使用 " + checkerType + " 检查产物 " + target + "。";
    }

    private List<ArtifactCheckResult> checks(ArtifactAcceptanceResult acceptance) {
        if (acceptance == null || acceptance.getChecks() == null) {
            return List.of();
        }
        return acceptance.getChecks().stream().filter(java.util.Objects::nonNull).toList();
    }

    private List<Map<String, Object>> acceptedArtifacts(AgentRunResult result,
                                                        ArtifactAcceptanceResult acceptance) {
        if (acceptance != null && acceptance.getArtifacts() != null) {
            return acceptance.getArtifacts();
        }
        return runtimeArtifacts(result);
    }

    private List<Map<String, Object>> runtimeArtifacts(AgentRunResult result) {
        if (result != null && result.getArtifacts() != null) {
            return result.getArtifacts();
        }
        return List.of();
    }

    private String summarize(String value, String fallback) {
        String summary = textOrDefault(value, fallback).replaceAll("\\s+", " ").trim();
        return summary.length() <= 512 ? summary : summary.substring(0, 512);
    }

    private String textOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private void appendRepairTurn(AgentRunCommand command, AgentRunResult previous, String repairMessage) {
        List<ChatMessage> messages = new ArrayList<>(command.getMessages());
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

    private void setExecutionAttempt(AgentRunCommand command, int attempt) {
        Map<String, Object> context = command.getContext() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(command.getContext());
        context.put("executionAttempt", Math.max(1, attempt));
        command.setContext(context);
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

    private AgentDefinitionSnapshot pythonRuntimeSnapshot(AgentConversationRequest request) {
        AgentDefinitionSnapshot snapshot = new AgentDefinitionSnapshot();
        snapshot.setAgentCode("python-agent-runtime");
        snapshot.setAgentVersion(1);
        snapshot.setRuntimeType(AgentRuntimeType.OPENAI_AGENTS_PYTHON);
        snapshot.setSdkVersion("python-local");
        snapshot.setSnapshotHash("python-local");
        snapshot.setWorkflowSnapshot(workflowSnapshot(request == null
                ? AgentArtifactDelivery.STANDARD : request.getArtifactDelivery()));
        return snapshot;
    }

    private void reconcileArtifactDelivery(AgentDefinitionSnapshot snapshot, AgentRunResult result) {
        if (snapshot == null) {
            return;
        }
        boolean renderRouteApplied = appliedRenderRoute(result);
        if (renderRouteApplied) {
            snapshot.setWorkflowSnapshot(workflowSnapshot(AgentArtifactDelivery.RENDER_DOCUMENT));
        } else if (runtimeInputRequired(result)) {
            snapshot.setWorkflowSnapshot(workflowSnapshot(AgentArtifactDelivery.STANDARD));
        }
    }

    private boolean appliedRenderRoute(AgentRunResult result) {
        if (result == null || result.getProviderMeta() == null
                || !(result.getProviderMeta().get("requestAnalysis") instanceof Map<?, ?> analysis)) {
            return false;
        }
        Object selectedAgentCode = analysis.get("selectedAgentCode");
        return AgentArtifactDelivery.forValidatedRoute(
                Boolean.TRUE.equals(analysis.get("routeApplied")),
                selectedAgentCode == null ? null : String.valueOf(selectedAgentCode)
        ) == AgentArtifactDelivery.RENDER_DOCUMENT;
    }

    private boolean runtimeInputRequired(AgentRunResult result) {
        return result != null && "INPUT_REQUIRED".equalsIgnoreCase(result.getStatus());
    }

    private Map<String, Object> workflowSnapshot(AgentArtifactDelivery delivery) {
        if (delivery != AgentArtifactDelivery.RENDER_DOCUMENT) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> finalAnswer = Map.of(
                "code", "final-answer",
                "artifactType", "TEXT",
                "contentFormat", "MARKDOWN",
                "required", true,
                "inlineSchema", Map.of("type", "string")
        );
        Map<String, Object> applicationBrief = optionalJsonArtifact("application-brief");
        Map<String, Object> dataContract = optionalJsonArtifact("data-contract");
        Map<String, Object> dataPreview = Map.of(
                "code", "data-preview",
                "artifactType", "JSON",
                "contentFormat", "JSON",
                "required", true,
                "inlineSchema", dataPreviewSchema()
        );
        Map<String, Object> applicationPlan = optionalJsonArtifact("application-plan");
        Map<String, Object> renderDocument = Map.of(
                "code", "render-document",
                "artifactType", "RENDER_JSON",
                "contentFormat", "JSON",
                "required", true,
                "inlineSchema", renderDocumentSchema()
        );
        Map<String, Object> validationReport = Map.of(
                "code", "validation-report",
                "artifactType", "JSON",
                "contentFormat", "JSON",
                "required", true,
                "inlineSchema", validationReportSchema()
        );
        Map<String, Object> applicationBuildState = optionalJsonArtifact("application-build-state");
        Map<String, Object> specification = Map.of(
                "artifacts", List.of(
                        finalAnswer,
                        applicationBrief,
                        dataContract,
                        dataPreview,
                        applicationPlan,
                        renderDocument,
                        validationReport,
                        applicationBuildState),
                "completionPolicy", Map.of(
                        "requireAllRequiredArtifacts", true,
                        "requireAllBlockingChecksPassed", true
                ),
                "repairPolicy", Map.of(
                        "maxRepairAttempts", 1,
                        "onExhausted", "FAILED"
                )
        );
        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("workflowRef", "workflow://render-document-delivery/v1");
        workflow.put("apiVersion", "ai.platform/v1alpha1");
        workflow.put("kind", "ArtifactWorkflow");
        workflow.put("metadata", Map.of("code", "render-document-delivery", "version", 1));
        workflow.put("spec", specification);
        return workflow;
    }

    private Map<String, Object> optionalJsonArtifact(String code) {
        return Map.of(
                "code", code,
                "artifactType", "JSON",
                "contentFormat", "JSON",
                "required", false,
                "inlineSchema", Map.of("type", "object")
        );
    }

    private Map<String, Object> dataPreviewSchema() {
        return Map.of(
                "type", "object",
                "required", List.of(
                        "tool", "success", "model", "catalogVersion",
                        "sourceRevision", "columns", "records"),
                "properties", Map.of(
                        "tool", Map.of(
                                "type", "string", "enum", List.of("data_preview_query_tool")),
                        "success", Map.of("type", "boolean", "enum", List.of(true)),
                        "model", Map.of("type", "string"),
                        "catalogVersion", Map.of("type", "integer"),
                        "sourceRevision", Map.of("type", "string"),
                        "columns", Map.of("type", "array"),
                        "records", Map.of("type", "array")
                )
        );
    }

    private Map<String, Object> renderDocumentSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("protocol", "protocolVersion", "pageId", "root"),
                "properties", Map.of(
                        "protocol", Map.of("type", "string", "enum", List.of("render-json")),
                        "protocolVersion", Map.of(
                                "type", "string", "enum", List.of("1.0", "1.0.0")),
                        "pageId", Map.of("type", "string"),
                        "root", Map.of("type", "object")
                )
        );
    }

    private Map<String, Object> validationReportSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("tool", "valid"),
                "properties", Map.of(
                        "tool", Map.of(
                                "type", "string", "enum", List.of("render_json_validate_tool")),
                        "valid", Map.of("type", "boolean", "enum", List.of(true))
                )
        );
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
