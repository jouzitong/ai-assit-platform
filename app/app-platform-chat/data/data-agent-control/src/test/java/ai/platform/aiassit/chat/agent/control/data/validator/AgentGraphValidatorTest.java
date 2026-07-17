package ai.platform.aiassit.chat.agent.control.data.validator;

import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.AgentControlDTOs;
import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.ValidationReportDTO;
import ai.platform.aiassit.chat.agent.control.data.support.ControlPlaneReferenceParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentGraphValidatorTest {

    private final AgentGraphValidator validator = new AgentGraphValidator(
            new ControlPlaneReferenceParser(), new AgentManifestValidator());

    @Test
    void rejectsCycleAcrossPublishedCollaborators() {
        AgentControlDTOs.Manifest root = manifest("agent-a", 1, "agent://agent-b/v1");
        Map<String, AgentControlDTOs.Manifest> definitions = Map.of(
                "agent-a@1", root,
                "agent-b@1", manifest("agent-b", 1, "agent://agent-a/v1"));

        ValidationReportDTO report = validator.validate(
                "agent-a", 1, root, resolver(definitions));

        assertThat(report.isValid()).isFalse();
        assertThat(report.getErrors()).anyMatch(message -> message.contains("cycle detected"));
    }

    @Test
    void rejectsDepthGreaterThanFour() {
        AgentControlDTOs.Manifest root = manifest("agent-a", 1, "agent://agent-b/v1");
        Map<String, AgentControlDTOs.Manifest> definitions = new LinkedHashMap<>();
        definitions.put("agent-b@1", manifest("agent-b", 1, "agent://agent-c/v1"));
        definitions.put("agent-c@1", manifest("agent-c", 1, "agent://agent-d/v1"));
        definitions.put("agent-d@1", manifest("agent-d", 1, "agent://agent-e/v1"));
        definitions.put("agent-e@1", manifest("agent-e", 1));

        ValidationReportDTO report = validator.validate(
                "agent-a", 1, root, resolver(definitions));

        assertThat(report.isValid()).isFalse();
        assertThat(report.getErrors()).anyMatch(message -> message.contains("maximum depth 4"));
    }

    @Test
    void rejectsMoreThanSixteenAgents() {
        List<String> childRefs = new ArrayList<>();
        Map<String, AgentControlDTOs.Manifest> definitions = new LinkedHashMap<>();
        for (int index = 1; index <= 16; index++) {
            String code = "child-" + index;
            childRefs.add("agent://" + code + "/v1");
            definitions.put(code + "@1", manifest(code, 1));
        }
        AgentControlDTOs.Manifest root = manifest("root", 1, childRefs.toArray(String[]::new));

        ValidationReportDTO report = validator.validate(
                "root", 1, root, resolver(definitions));

        assertThat(report.isValid()).isFalse();
        assertThat(report.getErrors()).anyMatch(message -> message.contains("maximum size 16"));
    }

    @Test
    void rejectsTwoVersionsOfSameAgentInOneGraph() {
        AgentControlDTOs.Manifest root = manifest(
                "root", 1, "agent://shared/v1", "agent://bridge/v1");
        Map<String, AgentControlDTOs.Manifest> definitions = Map.of(
                "shared@1", manifest("shared", 1),
                "shared@2", manifest("shared", 2),
                "bridge@1", manifest("bridge", 1, "agent://shared/v2"));

        ValidationReportDTO report = validator.validate(
                "root", 1, root, resolver(definitions));

        assertThat(report.isValid()).isFalse();
        assertThat(report.getErrors()).anyMatch(message -> message.contains("multiple versions"));
    }

    private AgentGraphValidator.PublishedAgentResolver resolver(
            Map<String, AgentControlDTOs.Manifest> definitions) {
        return (code, version) -> {
            AgentControlDTOs.Manifest manifest = definitions.get(code + "@" + version);
            return manifest == null ? null : new AgentGraphValidator.PublishedAgent(code, version, manifest);
        };
    }

    private AgentControlDTOs.Manifest manifest(String code, int version, String... refs) {
        AgentControlDTOs.Manifest manifest = new AgentControlDTOs.Manifest();
        manifest.getMetadata().setCode(code);
        manifest.getMetadata().setVersion(version);
        manifest.getMetadata().setName(code);
        manifest.getSpec().getInstructions().setText("Execute " + code + " safely.");
        AgentControlDTOs.ModelRef model = new AgentControlDTOs.ModelRef();
        model.setRef("model://default-quality");
        manifest.getSpec().setModel(model);
        List<AgentControlDTOs.CollaboratorRef> collaborators = new ArrayList<>();
        for (int index = 0; index < refs.length; index++) {
            AgentControlDTOs.CollaboratorRef collaborator = new AgentControlDTOs.CollaboratorRef();
            collaborator.setTargetAgentRef(refs[index]);
            collaborator.setMode("AS_TOOL");
            collaborator.setToolName("delegate_" + index);
            collaborators.add(collaborator);
        }
        manifest.getSpec().getCollaboration().setAgentTools(collaborators);
        return manifest;
    }
}
