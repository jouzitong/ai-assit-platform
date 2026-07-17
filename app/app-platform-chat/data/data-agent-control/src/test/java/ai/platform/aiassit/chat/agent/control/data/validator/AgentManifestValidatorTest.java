package ai.platform.aiassit.chat.agent.control.data.validator;

import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.AgentControlDTOs;
import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.ValidationReportDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentManifestValidatorTest {

    private final AgentManifestValidator validator = new AgentManifestValidator();

    @Test
    void acceptsPortableCanonicalAgentManifest() {
        ValidationReportDTO report = validator.validate("home-assistant", manifest());

        assertThat(report.isValid()).isTrue();
    }

    @Test
    void rejectsRuntimeDepthAndEmbeddedSecret() {
        AgentControlDTOs.Manifest manifest = manifest();
        manifest.getSpec().getRuntimeDefaults().setMaxAgentDepth(5);
        manifest.getSpec().getModel().setSettings(Map.of("apiKey", "raw-secret"));

        ValidationReportDTO report = validator.validate("home-assistant", manifest);

        assertThat(report.isValid()).isFalse();
        assertThat(report.getErrors()).anyMatch(message -> message.contains("maxAgentDepth"));
        assertThat(report.getErrors()).anyMatch(message -> message.contains("secret field"));
    }

    @Test
    void rejectsHandoffModeInsideAgentTools() {
        AgentControlDTOs.CollaboratorRef collaborator = collaborator("HANDOFF", "review_result");

        ValidationReportDTO report = validator.validate("home-assistant", manifestWithAgentTools(collaborator));

        assertThat(report.isValid()).isFalse();
        assertThat(report.getErrors())
                .contains("spec.collaboration.agentTools[0].mode must be AS_TOOL");
    }

    @Test
    void rejectsAsToolModeInsideHandoffs() {
        AgentControlDTOs.Manifest manifest = manifest();
        manifest.getSpec().getCollaboration().setHandoffs(List.of(collaborator("AS_TOOL", null)));

        ValidationReportDTO report = validator.validate("home-assistant", manifest);

        assertThat(report.isValid()).isFalse();
        assertThat(report.getErrors())
                .contains("spec.collaboration.handoffs[0].mode must be HANDOFF");
    }

    private AgentControlDTOs.Manifest manifestWithAgentTools(AgentControlDTOs.CollaboratorRef collaborator) {
        AgentControlDTOs.Manifest manifest = manifest();
        manifest.getSpec().getCollaboration().setAgentTools(List.of(collaborator));
        return manifest;
    }

    private AgentControlDTOs.CollaboratorRef collaborator(String mode, String toolName) {
        AgentControlDTOs.CollaboratorRef collaborator = new AgentControlDTOs.CollaboratorRef();
        collaborator.setTargetAgentRef("agent://result-reviewer/v1");
        collaborator.setMode(mode);
        collaborator.setToolName(toolName);
        return collaborator;
    }

    private AgentControlDTOs.Manifest manifest() {
        AgentControlDTOs.Manifest manifest = new AgentControlDTOs.Manifest();
        manifest.getMetadata().setCode("home-assistant");
        manifest.getMetadata().setVersion(1);
        manifest.getMetadata().setName("Home Assistant");
        manifest.getSpec().getInstructions().setText("Help the user.");
        AgentControlDTOs.ModelRef model = new AgentControlDTOs.ModelRef();
        model.setRef("model://default-quality");
        manifest.getSpec().setModel(model);
        return manifest;
    }
}
