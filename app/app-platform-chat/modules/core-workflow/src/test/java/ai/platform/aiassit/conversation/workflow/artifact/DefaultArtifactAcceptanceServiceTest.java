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
        assertThat(result.getArtifacts())
                .singleElement()
                .satisfies(artifact -> assertThat(artifact)
                        .containsEntry("artifactCode", "final-answer")
                        .containsEntry("content", "Completed result"));
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
}
