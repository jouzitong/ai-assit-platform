package ai.platform.aiassit.chat.agent.control.data.validator;

import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.ValidationReportDTO;
import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.WorkflowControlDTOs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowSpecificationValidatorTest {

    private final WorkflowSpecificationValidator validator = new WorkflowSpecificationValidator();

    @Test
    void acceptsCanonicalArtifactAcceptanceWorkflow() {
        WorkflowControlDTOs.Manifest manifest = manifest();

        ValidationReportDTO report = validator.validate(manifest);

        assertThat(report.isValid()).isTrue();
    }

    @Test
    void rejectsConflictingArtifactContractsAndUnknownCheckTarget() {
        WorkflowControlDTOs.Manifest manifest = manifest();
        WorkflowControlDTOs.Artifact artifact = manifest.getSpec().getArtifacts().get(0);
        artifact.setSchemaRef("schema://answer/v1");
        artifact.setInlineSchema(Map.of("type", "string"));
        manifest.getSpec().getChecks().get(0).setTargetArtifact("missing");

        ValidationReportDTO report = validator.validate(manifest);

        assertThat(report.isValid()).isFalse();
        assertThat(report.getErrors()).anyMatch(message -> message.contains("both schemaRef and inlineSchema"));
        assertThat(report.getErrors()).anyMatch(message -> message.contains("targetArtifact does not exist"));
    }

    private WorkflowControlDTOs.Manifest manifest() {
        WorkflowControlDTOs.Manifest manifest = new WorkflowControlDTOs.Manifest();
        manifest.getMetadata().setCode("answer-contract");
        manifest.getMetadata().setVersion(1);
        manifest.getMetadata().setName("Answer contract");

        WorkflowControlDTOs.Artifact artifact = new WorkflowControlDTOs.Artifact();
        artifact.setCode("answer");
        artifact.setName("Answer");
        artifact.setArtifactType("TEXT");
        artifact.setContentFormat("MARKDOWN");

        WorkflowControlDTOs.Check check = new WorkflowControlDTOs.Check();
        check.setCode("answer-schema");
        check.setName("Answer schema");
        check.setTargetArtifact("answer");
        check.setCheckerType("JSON_SCHEMA");
        check.setSeverity("ERROR");

        manifest.getSpec().setArtifacts(List.of(artifact));
        manifest.getSpec().setChecks(List.of(check));
        return manifest;
    }
}
