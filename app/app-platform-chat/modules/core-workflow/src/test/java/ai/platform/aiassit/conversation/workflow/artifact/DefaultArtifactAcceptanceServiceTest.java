package ai.platform.aiassit.conversation.workflow.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultArtifactAcceptanceServiceTest {

    private final DefaultArtifactAcceptanceService service =
            new DefaultArtifactAcceptanceService(new ObjectMapper());

    @Test
    void synthesizesAndAcceptsDefaultFinalAnswerArtifact() {
        ArtifactAcceptanceResult result = service.accept(Map.of(), List.of(), "Completed result");

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.getChecks())
                .singleElement()
                .satisfies(check -> {
                    assertThat(check.getCheckCode()).isEqualTo("required-final-answer");
                    assertThat(check.getCheckerType()).isEqualTo("REQUIRED");
                    assertThat(check.getStatus()).isEqualTo("PASSED");
                    assertThat(check.isPassed()).isTrue();
                });
        assertThat(result.getArtifacts())
                .singleElement()
                .satisfies(artifact -> assertThat(artifact)
                        .containsEntry("artifactCode", "final-answer")
                        .containsEntry("visible", false)
                        .containsEntry("content", "Completed result"));
    }

    @Test
    void preservesVisibilityOfProducedArtifactsWhileSyntheticFinalAnswerStaysHidden() {
        List<Map<String, Object>> artifacts = List.of(Map.of(
                "artifactCode", "render-document",
                "artifactType", "DOCUMENT",
                "visible", true,
                "content", "Rendered document"
        ));

        ArtifactAcceptanceResult result = service.accept(Map.of(), artifacts, "Completed result");

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.getArtifacts())
                .filteredOn(artifact -> "render-document".equals(artifact.get("artifactCode")))
                .singleElement()
                .satisfies(artifact -> assertThat(artifact).containsEntry("visible", true));
        assertThat(result.getArtifacts())
                .filteredOn(artifact -> "final-answer".equals(artifact.get("artifactCode")))
                .singleElement()
                .satisfies(artifact -> assertThat(artifact).containsEntry("visible", false));
    }

    @Test
    void validatesHiddenSyntheticFinalAnswerAgainstWorkflowContract() {
        Map<String, Object> workflow = Map.of("artifacts", List.of(Map.of(
                "code", "final-answer",
                "required", true,
                "inlineSchema", Map.of("type", "string")
        )));

        ArtifactAcceptanceResult result = service.accept(workflow, List.of(), "Completed result");

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.getChecks())
                .filteredOn(check -> "schema-final-answer".equals(check.getCheckCode()))
                .singleElement()
                .satisfies(check -> {
                    assertThat(check.getTargetArtifact()).isEqualTo("final-answer");
                    assertThat(check.isPassed()).isTrue();
                });
        assertThat(result.getChecks())
                .filteredOn(check -> "required-final-answer".equals(check.getCheckCode()))
                .singleElement()
                .satisfies(check -> assertThat(check.getStatus()).isEqualTo("PASSED"));
        assertThat(result.getArtifacts())
                .singleElement()
                .satisfies(artifact -> assertThat(artifact).containsEntry("visible", false));
    }

    @Test
    void failsClosedForUnsupportedBlockingChecker() {
        Map<String, Object> workflow = Map.of("checks", List.of(Map.of(
                "code", "quality-review",
                "checkerType", "AGENT",
                "targetArtifact", "final-answer",
                "blocking", true,
                "retryable", false
        )));

        ArtifactAcceptanceResult result = service.accept(workflow, List.of(), "answer");

        assertThat(result.isAccepted()).isFalse();
        assertThat(result.getChecks())
                .filteredOn(check -> "quality-review".equals(check.getCheckCode()))
                .singleElement()
                .satisfies(check -> {
                    assertThat(check.isBlocking()).isTrue();
                    assertThat(check.isPassed()).isFalse();
                    assertThat(check.getStatus()).isEqualTo("FAILED");
                });
    }

    @Test
    void reportsRepairableSchemaFailureAndInputRequiredExhaustion() {
        Map<String, Object> contract = Map.of(
                "code", "render-json",
                "required", true,
                "schema", Map.of(
                        "type", "object",
                        "required", List.of("title"),
                        "properties", Map.of("title", Map.of("type", "string"))
                )
        );
        Map<String, Object> workflow = Map.of(
                "artifacts", List.of(contract),
                "repairPolicy", Map.of("maxRepairAttempts", 2, "onExhausted", "INPUT_REQUIRED")
        );
        List<Map<String, Object>> artifacts = List.of(Map.of(
                "artifactCode", "render-json",
                "content", Map.of("unexpected", true)
        ));

        ArtifactAcceptanceResult result = service.accept(workflow, artifacts, "fallback");

        assertThat(result.isAccepted()).isFalse();
        assertThat(result.isRepairable()).isTrue();
        assertThat(result.isInputRequired()).isTrue();
        assertThat(result.getMaxRepairAttempts()).isEqualTo(2);
        assertThat(result.getRepairMessage()).contains("$.title is required");
    }

    @Test
    void skipsBlockingCheckWhenOptionalArtifactWasNotProduced() {
        Map<String, Object> workflow = Map.of(
                "apiVersion", "ai.platform/v1alpha1",
                "kind", "ArtifactWorkflow",
                "spec", Map.of(
                        "artifacts", List.of(Map.of(
                                "code", "render-document",
                                "artifactType", "RENDER_JSON",
                                "required", false,
                                "inlineSchema", Map.of("type", "object")
                        )),
                        "checks", List.of(Map.of(
                                "code", "render-schema",
                                "targetArtifact", "render-document",
                                "checkerType", "JSON_SCHEMA",
                                "blocking", true,
                                "retryable", true
                        ))
                ));

        ArtifactAcceptanceResult result = service.accept(workflow, List.of(), "plain answer");

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.getChecks())
                .singleElement()
                .satisfies(check -> assertThat(check.getStatus()).isEqualTo("SKIPPED"));
    }

    @Test
    void keepsValidRequiredRenderDocumentWhenOptionalArtifactsFailTheirContracts() {
        Map<String, Object> workflow = Map.of(
                "artifacts", List.of(
                        Map.of(
                                "code", "render-document",
                                "artifactType", "RENDER_JSON",
                                "contentFormat", "JSON",
                                "required", true,
                                "inlineSchema", Map.of("type", "object")
                        ),
                        Map.of(
                                "code", "application-plan",
                                "artifactType", "JSON",
                                "contentFormat", "JSON",
                                "required", false,
                                "inlineSchema", Map.of("type", "object")
                        ),
                        Map.of(
                                "code", "application-build-state",
                                "artifactType", "JSON",
                                "contentFormat", "JSON",
                                "required", false,
                                "inlineSchema", Map.of("type", "object")
                        )
                ),
                "repairPolicy", Map.of("maxRepairAttempts", 1)
        );
        List<Map<String, Object>> artifacts = List.of(
                Map.of(
                        "artifactCode", "render-document",
                        "artifactType", "RENDER_JSON",
                        "contentFormat", "JSON",
                        "content", Map.of("pageId", "addresses")
                ),
                Map.of(
                        "artifactCode", "application-plan",
                        "artifactType", "TEXT",
                        "contentFormat", "MARKDOWN",
                        "content", "not an object"
                ),
                Map.of(
                        "artifactCode", "application-build-state",
                        "artifactType", "TEXT",
                        "contentFormat", "MARKDOWN",
                        "content", "not an object"
                )
        );

        ArtifactAcceptanceResult result = service.accept(workflow, artifacts, "answer");

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.isRepairable()).isFalse();
        assertThat(result.getChecks())
                .filteredOn(check -> List.of("application-plan", "application-build-state")
                        .contains(check.getTargetArtifact()))
                .hasSize(6)
                .allSatisfy(check -> {
                    assertThat(check.isPassed()).isFalse();
                    assertThat(check.isBlocking()).isFalse();
                    assertThat(check.isRetryable()).isFalse();
                    assertThat(check.getSeverity()).isEqualTo("WARNING");
                    assertThat(check.getStatus()).isEqualTo("FAILED");
                });
        assertThat(result.getChecks())
                .filteredOn(check -> "required-render-document".equals(check.getCheckCode()))
                .singleElement()
                .satisfies(check -> {
                    assertThat(check.isPassed()).isTrue();
                    assertThat(check.isBlocking()).isTrue();
                });
    }

    @Test
    void failsClosedWhenPublishedSchemaReferenceWasNotResolved() {
        Map<String, Object> workflow = Map.of("artifacts", List.of(Map.of(
                "code", "render-document",
                "artifactType", "RENDER_JSON",
                "required", true,
                "schemaRef", "schema://render-document/v2"
        )));
        List<Map<String, Object>> artifacts = List.of(Map.of(
                "artifactCode", "render-document",
                "content", Map.of("title", "Example")
        ));

        ArtifactAcceptanceResult result = service.accept(workflow, artifacts, "answer");

        assertThat(result.isAccepted()).isFalse();
        assertThat(result.getRepairMessage()).contains("schemaRef was not resolved");
    }

    @Test
    void rejectsArtifactWhoseTypeDoesNotMatchThePublishedContract() {
        Map<String, Object> workflow = Map.of("artifacts", List.of(Map.of(
                "code", "render-document",
                "artifactType", "RENDER_JSON",
                "required", true
        )));
        List<Map<String, Object>> artifacts = List.of(Map.of(
                "artifactCode", "render-document",
                "artifactType", "TEXT",
                "content", Map.of("pageId", "addresses")
        ));

        ArtifactAcceptanceResult result = service.accept(workflow, artifacts, "answer");

        assertThat(result.isAccepted()).isFalse();
        assertThat(result.isRepairable()).isFalse();
        assertThat(result.getChecks())
                .filteredOn(check -> "type-render-document".equals(check.getCheckCode()))
                .singleElement()
                .satisfies(check -> {
                    assertThat(check.getCheckerType()).isEqualTo("ARTIFACT_TYPE");
                    assertThat(check.isBlocking()).isTrue();
                    assertThat(check.isRetryable()).isTrue();
                    assertThat(check.isPassed()).isFalse();
                    assertThat(check.getMessage()).contains("must be RENDER_JSON");
                });
    }

    @Test
    void explicitWorkflowRejectsUnknownAndDuplicateArtifactCodesFailClosed() {
        Map<String, Object> workflow = Map.of(
                "artifacts", List.of(
                        Map.of("code", "final-answer", "required", true),
                        Map.of("code", "render-document", "required", true)
                ),
                "completionPolicy", Map.of("requireAllBlockingChecksPassed", false)
        );
        List<Map<String, Object>> artifacts = List.of(
                Map.of("artifactCode", "final-answer", "content", "done"),
                Map.of("artifactCode", "render-document", "content", Map.of("pageId", "one")),
                Map.of("artifactCode", "render-document", "content", Map.of("pageId", "two")),
                Map.of("artifactCode", "shadow-render", "content", Map.of("pageId", "shadow"))
        );

        ArtifactAcceptanceResult result = service.accept(workflow, artifacts, "done");

        assertThat(result.isAccepted()).isFalse();
        assertThat(result.getChecks())
                .filteredOn(check -> "ARTIFACT_CODE".equals(check.getCheckerType()))
                .hasSize(2)
                .allSatisfy(check -> {
                    assertThat(check.isBlocking()).isTrue();
                    assertThat(check.isRetryable()).isTrue();
                    assertThat(check.isPassed()).isFalse();
                });
        assertThat(result.getRepairMessage())
                .contains("Artifact code must be unique within one result: render-document")
                .contains("Artifact code is not declared by the Workflow contract: shadow-render");
    }

    @Test
    void explicitWorkflowRejectsArtifactWhoseContentFormatDoesNotMatchContract() {
        Map<String, Object> workflow = Map.of("artifacts", List.of(Map.of(
                "code", "render-document",
                "contentFormat", "JSON",
                "required", true
        )));
        List<Map<String, Object>> artifacts = List.of(Map.of(
                "artifactCode", "render-document",
                "contentFormat", "MARKDOWN",
                "content", Map.of("pageId", "addresses")
        ));

        ArtifactAcceptanceResult result = service.accept(workflow, artifacts, null);

        assertThat(result.isAccepted()).isFalse();
        assertThat(result.getChecks())
                .filteredOn(check -> "format-render-document".equals(check.getCheckCode()))
                .singleElement()
                .satisfies(check -> {
                    assertThat(check.getCheckerType()).isEqualTo("CONTENT_FORMAT");
                    assertThat(check.isPassed()).isFalse();
                    assertThat(check.getMessage()).contains("must be JSON").contains("MARKDOWN");
                });
    }

    @Test
    void ordinaryTurnKeepsCompatibilityForUncontractedAndDuplicateArtifacts() {
        List<Map<String, Object>> artifacts = List.of(
                Map.of("artifactCode", "render-document", "contentFormat", "MARKDOWN", "content", "one"),
                Map.of("artifactCode", "render-document", "contentFormat", "JSON", "content", "two")
        );

        ArtifactAcceptanceResult result = service.accept(Map.of(), artifacts, "ordinary answer");

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.getChecks())
                .singleElement()
                .satisfies(check -> assertThat(check.getCheckCode()).isEqualTo("required-final-answer"));
        assertThat(result.getArtifacts()).hasSize(3);
    }
}
