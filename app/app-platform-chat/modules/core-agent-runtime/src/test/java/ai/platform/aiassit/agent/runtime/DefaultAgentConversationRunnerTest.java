package ai.platform.aiassit.agent.runtime;

import ai.platform.aiassit.conversation.workflow.artifact.ArtifactAcceptanceResult;
import ai.platform.aiassit.conversation.workflow.artifact.ArtifactAcceptanceService;
import ai.platform.aiassit.conversation.workflow.artifact.ArtifactCheckResult;
import ai.platform.aiassit.conversation.workflow.artifact.DefaultArtifactAcceptanceService;
import ai.platform.aiassit.model.entity.dto.AiModelConfigDTO;
import ai.platform.aiassit.model.service.AiModelConfigService;
import ai.platform.aiassit.service.ai.spi.agent.AgentCancellation;
import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionSnapshot;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunCommand;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunEvent;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunObserver;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunResult;
import ai.platform.aiassit.service.ai.spi.agent.AgentRuntime;
import ai.platform.aiassit.service.ai.spi.agent.AgentRuntimeCapabilities;
import ai.platform.aiassit.service.ai.spi.agent.AgentRuntimeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.arthena.framework.common.exception.BizException;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultAgentConversationRunnerTest {

    @Test
    void explicitModelSelectionIsResolvedWithoutAnAgentManifest() {
        AiModelConfigDTO selected = model(42L, "selected", "qwen-plus");
        DefaultAgentConversationRunner runner = runner(modelService(Map.of(42L, selected)));
        AgentConversationRequest request = new AgentConversationRequest();
        request.setModelId(42L);

        assertThat(runner.resolveModel(request)).isSameAs(selected);
    }

    @Test
    void missingExplicitModelIsRejected() {
        DefaultAgentConversationRunner runner = runner(modelService(Map.of()));
        AgentConversationRequest request = new AgentConversationRequest();
        request.setModelId(99L);

        assertThatThrownBy(() -> runner.resolveModel(request))
                .satisfies(error -> assertThat(error.toString()).contains("99"));
    }

    @Test
    void disabledExplicitModelIsRejectedAtRuntimeBoundary() {
        AiModelConfigDTO disabled = model(42L, "disabled", "qwen-plus");
        disabled.setEnabled(false);
        DefaultAgentConversationRunner runner = runner(modelService(Map.of(42L, disabled)));
        AgentConversationRequest request = new AgentConversationRequest();
        request.setModelId(42L);

        assertThatThrownBy(() -> runner.resolveModel(request))
                .satisfies(error -> assertThat(error.toString()).contains("42"));
    }

    @Test
    void ordinaryTextTurnKeepsDefaultFinalAnswerAcceptance() {
        StubRuntime runtime = runtime(result("ordinary answer"));
        DefaultAgentConversationRunner runner = executableRunner(runtime,
                new DefaultArtifactAcceptanceService(new ObjectMapper()));

        AgentConversationOutcome outcome = runner.run(request(), AgentRunObserver.NOOP, AgentCancellation.NONE);

        assertThat(outcome.getStatus()).isEqualTo("SUCCESS");
        assertThat(runtime.invocations).isEqualTo(1);
        assertThat(runtime.snapshots)
                .singleElement()
                .satisfies(snapshot -> assertThat(snapshot.getWorkflowSnapshot()).isEmpty());
    }

    @Test
    void runtimeClarificationDoesNotRequireRenderArtifacts() {
        AgentRunResult clarification = result("请补充看板的数据范围和需要展示的指标。");
        clarification.setStatus("INPUT_REQUIRED");
        markAppliedRoute(clarification, false, "dashboard-application-builder");
        StubRuntime runtime = runtime(clarification);
        DefaultArtifactAcceptanceService delegate = new DefaultArtifactAcceptanceService(new ObjectMapper());
        List<Map<String, Object>> acceptedWorkflows = new ArrayList<>();
        DefaultAgentConversationRunner runner = executableRunner(runtime, (workflow, artifacts, answer) -> {
            acceptedWorkflows.add(Map.copyOf(workflow));
            return delegate.accept(workflow, artifacts, answer);
        });
        AgentConversationRequest request = request();
        request.setTarget(AgentTarget.explicit("dashboard-application-builder", 1));
        request.setArtifactDelivery(AgentArtifactDelivery.RENDER_DOCUMENT);
        List<AgentRunEvent> events = new ArrayList<>();

        AgentConversationOutcome outcome = runner.run(request, events::add, AgentCancellation.NONE);

        assertThat(outcome.getStatus()).isEqualTo("INPUT_REQUIRED");
        assertThat(outcome.getAnswer()).isEqualTo("请补充看板的数据范围和需要展示的指标。");
        assertThat(runtime.invocations).isEqualTo(1);
        assertThat(acceptedWorkflows).containsExactly(Map.of());
        assertThat(outcome.getArtifacts())
                .extracting(artifact -> artifact.get("artifactCode"))
                .containsExactly("final-answer");
        assertThat(event(events, "execution.result.completed").getExt())
                .containsEntry("resultStatus", "INPUT_REQUIRED");
    }

    @Test
    void renderDeliveryCannotPassAcceptanceWithOnlyFinalAnswerText() {
        AgentRunResult routedTextOnly = result("text only");
        routedTextOnly.setStatus("INPUT_REQUIRED");
        markAppliedRoute(routedTextOnly, true, "dashboard-application-builder");
        StubRuntime runtime = runtime(routedTextOnly, result("still text only"));
        DefaultAgentConversationRunner runner = executableRunner(runtime,
                new DefaultArtifactAcceptanceService(new ObjectMapper()));
        AgentConversationRequest request = request();
        List<AgentRunEvent> events = new ArrayList<>();

        assertThatThrownBy(() -> runner.run(request, events::add, AgentCancellation.NONE))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(error.toString()).contains("required-render-document"));

        assertThat(runtime.invocations).isEqualTo(2);
        assertThat(runtime.executionAttempts).containsExactly(1, 2);
        assertThat(runtime.snapshots).hasSize(2);
        assertThat(runtime.snapshots.get(0).getWorkflowSnapshot()).isEmpty();
        assertThat(runtime.snapshots.get(1).getWorkflowSnapshot())
                .containsEntry("workflowRef", "workflow://render-document-delivery/v1");
        assertThat(list(map(runtime.snapshots.get(1).getWorkflowSnapshot().get("spec")).get("artifacts")))
                .filteredOn(contract -> List.of(
                                "application-brief",
                                "data-contract",
                                "application-plan",
                                "application-build-state")
                        .contains(contract.get("code")))
                .hasSize(4)
                .allSatisfy(contract -> assertThat(contract)
                        .containsEntry("artifactType", "JSON")
                        .containsEntry("contentFormat", "JSON")
                        .containsEntry("required", false)
                        .containsEntry("inlineSchema", Map.of("type", "object")));
        assertThat(events)
                .filteredOn(event -> "check.completed".equals(event.getEventType())
                        && "required-render-document".equals(event.getExt().get("checkCode")))
                .hasSize(2)
                .allSatisfy(event -> {
                    assertThat(event.getStatus()).isEqualTo("FAILED");
                    assertThat(event.getExt()).containsEntry("passed", false);
                });
        assertThat(events)
                .filteredOn(event -> "artifact.repair.requested".equals(event.getEventType()))
                .hasSize(1);
        assertThat(event(events, "execution.result.completed").getExt())
                .containsEntry("resultStatus", "FAILED")
                .containsEntry("remediationAttempts", 1);
    }

    @Test
    void renderDeliveryAcceptsAValidRenderJsonArtifact() {
        AgentRunResult renderResult = validRenderResult();
        StubRuntime runtime = runtime(renderResult);
        DefaultAgentConversationRunner runner = executableRunner(runtime,
                new DefaultArtifactAcceptanceService(new ObjectMapper()));
        AgentConversationRequest request = request();
        List<AgentRunEvent> events = new ArrayList<>();

        AgentConversationOutcome outcome = runner.run(request, events::add, AgentCancellation.NONE);

        assertThat(outcome.getStatus()).isEqualTo("SUCCESS");
        assertThat(outcome.getArtifacts())
                .filteredOn(artifact -> "render-document".equals(artifact.get("artifactCode")))
                .singleElement()
                .satisfies(artifact -> assertThat(artifact)
                        .containsEntry("artifactType", "RENDER_JSON")
                        .containsEntry("visible", true));
        assertThat(event(events, "execution.result.completed").getExt())
                .containsEntry("resultStatus", "SUCCESS")
                .containsEntry("artifactCount", 4);
        assertThat(events)
                .filteredOn(event -> "check.completed".equals(event.getEventType()))
                .allSatisfy(event -> assertThat(event.getStatus()).isEqualTo("SUCCESS"));
    }

    @Test
    void renderDeliveryAcceptsDeclaredOptionalDashboardArtifactsWhenProduced() {
        AgentRunResult renderResult = validRenderResult();
        List<Map<String, Object>> artifacts = new ArrayList<>(renderResult.getArtifacts());
        artifacts.addAll(List.of(
                optionalJsonArtifact("application-brief"),
                optionalJsonArtifact("data-contract"),
                optionalJsonArtifact("application-plan"),
                optionalJsonArtifact("application-build-state")
        ));
        renderResult.setArtifacts(artifacts);
        StubRuntime runtime = runtime(renderResult);
        DefaultAgentConversationRunner runner = executableRunner(runtime,
                new DefaultArtifactAcceptanceService(new ObjectMapper()));

        AgentConversationOutcome outcome = runner.run(
                request(), AgentRunObserver.NOOP, AgentCancellation.NONE);

        assertThat(outcome.getStatus()).isEqualTo("SUCCESS");
        assertThat(outcome.getArtifacts())
                .extracting(artifact -> artifact.get("artifactCode"))
                .contains(
                        "application-brief",
                        "data-contract",
                        "application-plan",
                        "application-build-state");
    }

    @Test
    void renderDeliveryStillRejectsAnArtifactOutsideTheExpandedContract() {
        AgentRunResult invalidResult = validRenderResult();
        List<Map<String, Object>> artifacts = new ArrayList<>(invalidResult.getArtifacts());
        artifacts.add(optionalJsonArtifact("shadow-render"));
        invalidResult.setArtifacts(artifacts);
        StubRuntime runtime = runtime(invalidResult, invalidResult);
        DefaultAgentConversationRunner runner = executableRunner(runtime,
                new DefaultArtifactAcceptanceService(new ObjectMapper()));
        List<AgentRunEvent> events = new ArrayList<>();

        assertThatThrownBy(() -> runner.run(request(), events::add, AgentCancellation.NONE))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(error.toString()).contains("shadow-render"));

        assertThat(runtime.invocations).isEqualTo(2);
        assertThat(events)
                .filteredOn(event -> "check.completed".equals(event.getEventType())
                        && "ARTIFACT_CODE".equals(event.getExt().get("checkerType")))
                .hasSize(2)
                .allSatisfy(event -> assertThat(event.getStatus()).isEqualTo("FAILED"));
    }

    @Test
    void unappliedRenderRouteRecommendationDoesNotChangeOrdinaryChatAcceptance() {
        AgentRunResult recommendation = result("ordinary answer");
        markAppliedRoute(recommendation, false, "dashboard-application-builder");
        StubRuntime runtime = runtime(recommendation);
        DefaultAgentConversationRunner runner = executableRunner(runtime,
                new DefaultArtifactAcceptanceService(new ObjectMapper()));

        AgentConversationOutcome outcome = runner.run(request(), AgentRunObserver.NOOP, AgentCancellation.NONE);

        assertThat(outcome.getStatus()).isEqualTo("SUCCESS");
        assertThat(runtime.invocations).isEqualTo(1);
        assertThat(runtime.snapshots)
                .singleElement()
                .satisfies(snapshot -> assertThat(snapshot.getWorkflowSnapshot()).isEmpty());
    }

    @Test
    void acceptedRunEmitsAuthoritativeExecutionResult() {
        ArtifactCheckResult schemaCheck = check("schema-final-answer", true, true, false,
                "PASSED", "JSON Schema check passed");
        ArtifactAcceptanceResult accepted = acceptance(true, false, false, 2,
                "", schemaCheck);
        AgentRunResult acceptedResult = result("answer");
        acceptedResult.getProviderMeta().put("confidence", Map.of("confidence", 0.86d));
        StubRuntime runtime = runtime(acceptedResult);
        DefaultAgentConversationRunner runner = executableRunner(runtime, acceptanceService(accepted));
        List<AgentRunEvent> events = new ArrayList<>();

        AgentConversationOutcome outcome = runner.run(request(), events::add, AgentCancellation.NONE);

        AgentRunEvent completed = event(events, "execution.result.completed");
        assertThat(outcome.getStatus()).isEqualTo("SUCCESS");
        assertThat(completed.getStatus()).isEqualTo("SUCCESS");
        assertThat(completed.getExt())
                .containsEntry("activityCode", "execution-result")
                .containsEntry("activityType", "EXECUTION_RESULT")
                .containsEntry("authoritative", true)
                .containsEntry("resultStatus", "SUCCESS")
                .containsEntry("outcomeStatus", "SUCCESS")
                .containsEntry("repairAttempts", 0)
                .containsEntry("remediationAttempts", 0)
                .containsEntry("completionCoverage", 1.0d)
                .containsEntry("answerConfidence", 0.86d)
                .containsEntry("artifactCount", 1);
        assertThat(map(completed.getExt().get("checks")))
                .containsEntry("total", 1)
                .containsEntry("passed", 1)
                .containsEntry("failed", 0)
                .containsEntry("blockingFailed", 0);
        assertThat(map(completed.getExt().get("nextAction")))
                .containsEntry("type", "DELIVER_RESULT");
        assertThat(list(completed.getExt().get("remainingIssues"))).isEmpty();
        assertThat(event(events, "check.completed").getStatus()).isEqualTo("SUCCESS");
        assertThat(event(events, "check.completed").getExt()).containsEntry("checkStatus", "PASSED");
        assertThat(runtime.executionAttempts).containsExactly(1);
    }

    @Test
    void acceptedRunWithNonBlockingIssueIsReportedAsPartialWithoutChangingOutcomeCompatibility() {
        ArtifactCheckResult advisoryCheck = check("quality-advisory", false, false, false,
                "FAILED", "Answer should include another source");
        ArtifactAcceptanceResult accepted = acceptance(true, false, false, 0,
                "", advisoryCheck);
        DefaultAgentConversationRunner runner = executableRunner(runtime(result("answer")),
                acceptanceService(accepted));
        List<AgentRunEvent> events = new ArrayList<>();

        AgentConversationOutcome outcome = runner.run(request(), events::add, AgentCancellation.NONE);

        AgentRunEvent completed = event(events, "execution.result.completed");
        assertThat(outcome.getStatus()).isEqualTo("SUCCESS");
        assertThat(completed.getStatus()).isEqualTo("PARTIAL");
        assertThat(completed.getExt())
                .containsEntry("resultStatus", "PARTIAL")
                .containsEntry("outcomeStatus", "PARTIAL")
                .containsEntry("completionCoverage", 0.0d);
        assertThat(map(completed.getExt().get("checks")))
                .containsEntry("failed", 1)
                .containsEntry("blockingFailed", 0);
        assertThat(list(completed.getExt().get("remainingIssues")))
                .singleElement()
                .satisfies(issue -> assertThat(issue)
                        .containsEntry("checkCode", "quality-advisory")
                        .containsEntry("blocking", false));
        assertThat(map(completed.getExt().get("nextAction")))
                .containsEntry("type", "REVIEW_REMAINING_ISSUES");
    }

    @Test
    void repairAndAcceptanceAttemptsShareStableActivityIdentities() {
        ArtifactCheckResult failedCheck = check("schema-final-answer", false, true, true,
                "FAILED", "Required field is missing");
        ArtifactCheckResult passedCheck = check("schema-final-answer", true, true, true,
                "PASSED", "JSON Schema check passed");
        ArtifactAcceptanceResult rejected = acceptance(false, true, false, 2,
                "Add the required field", failedCheck);
        ArtifactAcceptanceResult accepted = acceptance(true, false, false, 2,
                "", passedCheck);
        StubRuntime runtime = runtime(result("incomplete"), result("repaired"));
        DefaultAgentConversationRunner runner = executableRunner(runtime,
                acceptanceService(rejected, accepted));
        List<AgentRunEvent> events = new ArrayList<>();

        AgentConversationOutcome outcome = runner.run(request(), events::add, AgentCancellation.NONE);

        assertThat(outcome.getStatus()).isEqualTo("SUCCESS");
        assertThat(runtime.invocations).isEqualTo(2);
        assertThat(runtime.executionAttempts).containsExactly(1, 2);
        for (int attempt = 1; attempt <= 2; attempt++) {
            AgentRunEvent started = event(events, "check.started", attempt);
            AgentRunEvent completed = event(events, "check.completed", attempt);
            assertThat(started.getExt().get("activityCode"))
                    .isEqualTo(completed.getExt().get("activityCode"));
            assertThat(started.getExt()).containsEntry("repairAttempt", attempt - 1);
        }
        AgentRunEvent repairRequested = event(events, "artifact.repair.requested");
        AgentRunEvent repairCompleted = event(events, "artifact.repair.completed");
        assertThat(repairRequested.getExt().get("activityCode"))
                .isEqualTo(repairCompleted.getExt().get("activityCode"));
        assertThat(repairCompleted.getExt()).containsEntry("repairAttempt", 1);
        assertThat(event(events, "execution.result.completed").getExt())
                .containsEntry("repairAttempts", 1)
                .containsEntry("remediationAttempts", 1)
                .containsEntry("attempt", 2)
                .containsEntry("completionCoverage", 1.0d)
                .containsEntry("resultStatus", "SUCCESS");
    }

    @Test
    void failedRepairClosesRepairActivityAndEmitsFailedExecutionResult() {
        ArtifactCheckResult failedCheck = check("schema-final-answer", false, true, true,
                "FAILED", "Required field is missing");
        ArtifactAcceptanceResult rejected = acceptance(false, true, false, 2,
                "Add the required field", failedCheck);
        StubRuntime runtime = runtime(result("incomplete"), new IllegalStateException("provider unavailable"));
        DefaultAgentConversationRunner runner = executableRunner(runtime, acceptanceService(rejected));
        List<AgentRunEvent> events = new ArrayList<>();

        assertThatThrownBy(() -> runner.run(request(), events::add, AgentCancellation.NONE))
                .isInstanceOf(IllegalStateException.class);

        AgentRunEvent repairRequested = event(events, "artifact.repair.requested");
        AgentRunEvent repairFailed = event(events, "artifact.repair.failed");
        assertThat(repairFailed.getStatus()).isEqualTo("FAILED");
        assertThat(repairFailed.getExt())
                .containsEntry("activityCode", repairRequested.getExt().get("activityCode"))
                .containsEntry("repairAttempt", 1)
                .containsEntry("failureType", "IllegalStateException");
        assertThat(events).noneMatch(event -> "artifact.repair.completed".equals(event.getEventType()));
        assertThat(runtime.executionAttempts).containsExactly(1, 2);
        assertThat(event(events, "execution.result.completed").getExt())
                .containsEntry("authoritative", true)
                .containsEntry("resultStatus", "FAILED")
                .containsEntry("outcomeStatus", "FAILED")
                .containsEntry("remediationAttempts", 1)
                .containsEntry("completionCoverage", 0.0d);
    }

    @Test
    void exhaustedAcceptanceRequestsUserInput() {
        ArtifactCheckResult failedCheck = check("required-context", false, true, false,
                "FAILED", "Repository name is required");
        ArtifactAcceptanceResult inputRequired = acceptance(false, false, true, 1,
                "请补充需要分析的仓库名称。", failedCheck);
        DefaultAgentConversationRunner runner = executableRunner(runtime(result("draft")),
                acceptanceService(inputRequired));
        List<AgentRunEvent> events = new ArrayList<>();

        AgentConversationOutcome outcome = runner.run(request(), events::add, AgentCancellation.NONE);

        AgentRunEvent completed = event(events, "execution.result.completed");
        assertThat(outcome.getStatus()).isEqualTo("INPUT_REQUIRED");
        assertThat(outcome.getAnswer()).isEqualTo("请补充需要分析的仓库名称。");
        assertThat(completed.getStatus()).isEqualTo("INPUT_REQUIRED");
        assertThat(completed.getExt()).containsEntry("resultStatus", "INPUT_REQUIRED");
        assertThat(map(completed.getExt().get("nextAction")))
                .containsEntry("type", "REQUEST_USER_INPUT");
    }

    @Test
    void runtimeFailureEmitsFailedExecutionResultWithoutLeakingItsMessage() {
        StubRuntime runtime = runtime(new IllegalStateException("secret provider response"));
        DefaultAgentConversationRunner runner = executableRunner(runtime,
                (workflow, artifacts, answer) -> {
                    throw new AssertionError("acceptance should not run");
                });
        List<AgentRunEvent> events = new ArrayList<>();

        assertThatThrownBy(() -> runner.run(request(), events::add, AgentCancellation.NONE))
                .isInstanceOf(IllegalStateException.class);

        AgentRunEvent completed = event(events, "execution.result.completed");
        assertThat(completed.getStatus()).isEqualTo("FAILED");
        assertThat(completed.getMessage()).doesNotContain("secret provider response");
        assertThat(completed.getExt())
                .containsEntry("resultStatus", "FAILED")
                .containsEntry("failureType", "IllegalStateException");
        assertThat(map(completed.getExt().get("nextAction")))
                .containsEntry("type", "STOP_AND_REPORT_FAILURE");
        assertThat(list(completed.getExt().get("remainingIssues"))).hasSize(1);
    }

    @Test
    void cancelledRuntimeEmitsCancelledExecutionResult() {
        StubRuntime runtime = runtime(new IllegalStateException("cancelled"));
        DefaultAgentConversationRunner runner = executableRunner(runtime,
                (workflow, artifacts, answer) -> {
                    throw new AssertionError("acceptance should not run");
                });
        List<AgentRunEvent> events = new ArrayList<>();

        assertThatThrownBy(() -> runner.run(request(), events::add, () -> true))
                .isInstanceOf(IllegalStateException.class);

        AgentRunEvent completed = event(events, "execution.result.completed");
        assertThat(completed.getStatus()).isEqualTo("CANCELLED");
        assertThat(completed.getExt()).containsEntry("resultStatus", "CANCELLED");
        assertThat(map(completed.getExt().get("nextAction"))).containsEntry("type", "NONE");
    }

    private DefaultAgentConversationRunner runner(AiModelConfigService modelConfigService) {
        return new DefaultAgentConversationRunner(
                modelConfigService,
                null,
                List.of(),
                List.of(),
                new ObjectMapper());
    }

    private DefaultAgentConversationRunner executableRunner(AgentRuntime runtime,
                                                             ArtifactAcceptanceService acceptanceService) {
        AiModelConfigDTO selected = model(42L, "selected", "qwen-plus");
        return new DefaultAgentConversationRunner(
                modelService(Map.of(42L, selected)),
                acceptanceService,
                List.of(runtime),
                List.of(),
                new ObjectMapper());
    }

    private AgentConversationRequest request() {
        AgentConversationRequest request = new AgentConversationRequest();
        request.setRunId("run-1");
        request.setRequestId("request-1");
        request.setTraceId("trace-1");
        request.setSessionCode("session-1");
        request.setRoundCode("round-1");
        request.setModelId(42L);
        request.setInput("Analyze the repository and propose a safe implementation plan.");
        return request;
    }

    private StubRuntime runtime(Object... results) {
        return new StubRuntime(results);
    }

    private AgentRunResult result(String answer) {
        AgentRunResult result = new AgentRunResult();
        result.setFinalOutput(answer);
        result.setArtifacts(List.of(Map.of(
                "artifactCode", "final-answer",
                "artifactType", "TEXT",
                "content", answer
        )));
        return result;
    }

    private AgentRunResult validRenderResult() {
        AgentRunResult renderResult = result("rendered address list");
        renderResult.setArtifacts(List.of(
                Map.of(
                        "artifactCode", "final-answer",
                        "artifactType", "TEXT",
                        "contentFormat", "MARKDOWN",
                        "content", "rendered address list"
                ),
                Map.of(
                        "artifactCode", "data-preview",
                        "artifactType", "JSON",
                        "contentFormat", "JSON",
                        "content", Map.of(
                                "tool", "data_preview_query_tool",
                                "success", true,
                                "model", "ods_trade_account_user_address",
                                "catalogVersion", 7,
                                "sourceRevision", "virtual-model/v7",
                                "columns", List.of(Map.of("name", "id")),
                                "records", List.of(Map.of("id", 1))
                        )
                ),
                Map.of(
                        "artifactCode", "render-document",
                        "artifactType", "RENDER_JSON",
                        "contentFormat", "JSON",
                        "visible", true,
                        "content", Map.of(
                                "protocol", "render-json",
                                "protocolVersion", "1.0.0",
                                "pageId", "user-addresses",
                                "root", Map.of("component", "zg-common-list")
                        )
                ),
                Map.of(
                        "artifactCode", "validation-report",
                        "artifactType", "JSON",
                        "contentFormat", "JSON",
                        "content", Map.of(
                                "tool", "render_json_validate_tool",
                                "valid", true
                        )
                )
        ));
        markAppliedRoute(renderResult, true, "dashboard-application-builder");
        return renderResult;
    }

    private Map<String, Object> optionalJsonArtifact(String artifactCode) {
        return Map.of(
                "artifactCode", artifactCode,
                "artifactType", "JSON",
                "contentFormat", "JSON",
                "content", Map.of("section", artifactCode)
        );
    }

    private void markAppliedRoute(AgentRunResult result, boolean routeApplied, String selectedAgentCode) {
        result.getProviderMeta().put("requestAnalysis", Map.of(
                "routeApplied", routeApplied,
                "selectedAgentCode", selectedAgentCode
        ));
    }

    private ArtifactAcceptanceService acceptanceService(ArtifactAcceptanceResult... results) {
        Deque<ArtifactAcceptanceResult> queue = new ArrayDeque<>(List.of(results));
        return (workflow, artifacts, answer) -> queue.removeFirst();
    }

    private ArtifactAcceptanceResult acceptance(boolean accepted,
                                                  boolean repairable,
                                                  boolean inputRequired,
                                                  int maxRepairAttempts,
                                                  String repairMessage,
                                                  ArtifactCheckResult... checks) {
        ArtifactAcceptanceResult result = new ArtifactAcceptanceResult();
        result.setAccepted(accepted);
        result.setRepairable(repairable);
        result.setInputRequired(inputRequired);
        result.setMaxRepairAttempts(maxRepairAttempts);
        result.setOnExhausted(inputRequired ? "INPUT_REQUIRED" : "FAILED");
        result.setRepairMessage(repairMessage);
        result.setArtifacts(List.of(Map.of(
                "artifactCode", "final-answer",
                "artifactType", "TEXT",
                "content", "accepted answer"
        )));
        result.setChecks(List.of(checks));
        return result;
    }

    private ArtifactCheckResult check(String code,
                                      boolean passed,
                                      boolean blocking,
                                      boolean retryable,
                                      String status,
                                      String message) {
        return ArtifactCheckResult.builder()
                .checkCode(code)
                .targetArtifact("final-answer")
                .checkerType("JSON_SCHEMA")
                .severity(blocking ? "ERROR" : "WARNING")
                .blocking(blocking)
                .retryable(retryable)
                .passed(passed)
                .status(status)
                .message(message)
                .build();
    }

    private AgentRunEvent event(List<AgentRunEvent> events, String eventType) {
        return events.stream()
                .filter(event -> eventType.equals(event.getEventType()))
                .findFirst()
                .orElseThrow();
    }

    private AgentRunEvent event(List<AgentRunEvent> events, String eventType, int attempt) {
        return events.stream()
                .filter(event -> eventType.equals(event.getEventType()))
                .filter(event -> Integer.valueOf(attempt).equals(event.getExt().get("attempt")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private AiModelConfigDTO model(Long id, String modelCode, String apiModel) {
        AiModelConfigDTO model = new AiModelConfigDTO();
        model.setId(id);
        model.setModelCode(modelCode);
        model.setApiModel(apiModel);
        model.setBaseUrl("https://model.example/v1");
        model.setEnabled(true);
        return model;
    }

    private AiModelConfigService modelService(Map<Long, AiModelConfigDTO> byId) {
        return (AiModelConfigService) Proxy.newProxyInstance(
                AiModelConfigService.class.getClassLoader(),
                new Class<?>[]{AiModelConfigService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getResolvedById" -> byId.get(args[0]);
                    case "selectEnabledModels" -> List.of();
                    case "toString" -> "AiModelConfigServiceStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static final class StubRuntime implements AgentRuntime {
        private final Deque<Object> results;
        private final List<Integer> executionAttempts = new ArrayList<>();
        private final List<AgentDefinitionSnapshot> snapshots = new ArrayList<>();
        private int invocations;

        private StubRuntime(Object... results) {
            this.results = new ArrayDeque<>(List.of(results));
        }

        @Override
        public AgentRuntimeCapabilities capabilities() {
            return AgentRuntimeCapabilities.builder()
                    .runtimeType(AgentRuntimeType.OPENAI_AGENTS_PYTHON)
                    .sdkVersion("test")
                    .build();
        }

        @Override
        public AgentRunResult run(AgentDefinitionSnapshot snapshot,
                                  AgentRunCommand command,
                                  AgentRunObserver observer,
                                  AgentCancellation cancellation) {
            invocations++;
            executionAttempts.add((Integer) command.getContext().get("executionAttempt"));
            snapshots.add(new ObjectMapper().convertValue(snapshot, AgentDefinitionSnapshot.class));
            Object next = results.removeFirst();
            if (next instanceof RuntimeException error) {
                throw error;
            }
            return (AgentRunResult) next;
        }
    }
}
