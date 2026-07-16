package ai.platform.aiassit.chat.workflow.data.validator;

import ai.platform.aiassit.chat.workflow.data.entity.dto.control.AgentControlDTOs;
import ai.platform.aiassit.chat.workflow.data.entity.dto.control.ValidationReportDTO;
import org.junit.jupiter.api.Test;

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
