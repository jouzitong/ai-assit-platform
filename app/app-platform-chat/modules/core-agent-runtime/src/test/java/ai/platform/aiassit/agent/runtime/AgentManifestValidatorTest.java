package ai.platform.aiassit.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentManifestValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentManifestValidator validator = new AgentManifestValidator();

    @Test
    void acceptsPortableAgentManifest() throws Exception {
        JsonNode manifest = objectMapper.readTree("""
                {
                  "apiVersion": "ai.platform/v1alpha1",
                  "kind": "Agent",
                  "metadata": {"code": "manager", "name": "Manager"},
                  "spec": {
                    "instructions": {"type": "inline", "text": "Coordinate the task"},
                    "model": {"ref": "model://default-quality"},
                    "runtimeDefaults": {"maxTurns": 12, "maxAgentDepth": 4, "timeoutMs": 120000},
                    "collaboration": {
                      "agentTools": [{
                        "targetAgentRef": "agent://reviewer/v1",
                        "mode": "AS_TOOL",
                        "toolName": "review_result"
                      }],
                      "handoffs": []
                    }
                  }
                }
                """);

        assertThatCode(() -> validator.validate(manifest, "manager")).doesNotThrowAnyException();
    }

    @Test
    void rejectsEmbeddedCredentials() throws Exception {
        JsonNode manifest = objectMapper.readTree("""
                {
                  "apiVersion": "ai.platform/v1alpha1",
                  "kind": "Agent",
                  "metadata": {"code": "manager", "name": "Manager"},
                  "spec": {
                    "instructions": {"type": "inline", "text": "Coordinate"},
                    "model": {"ref": "model://default", "apiKey": "sk-should-not-be-here"}
                  }
                }
                """);

        assertThatThrownBy(() -> validator.validate(manifest, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain secret field");
    }

    @Test
    void rejectsDuplicateCollaborationToolNames() throws Exception {
        JsonNode manifest = objectMapper.readTree("""
                {
                  "apiVersion": "ai.platform/v1alpha1",
                  "kind": "Agent",
                  "metadata": {"code": "manager", "name": "Manager"},
                  "spec": {
                    "instructions": {"type": "inline", "text": "Coordinate"},
                    "model": {"ref": "model://default"},
                    "collaboration": {
                      "agentTools": [
                        {"targetAgentRef": "agent://one/v1", "mode": "AS_TOOL", "toolName": "delegate"},
                        {"targetAgentRef": "agent://two/v1", "mode": "AS_TOOL", "toolName": "delegate"}
                      ]
                    }
                  }
                }
                """);

        assertThatThrownBy(() -> validator.validate(manifest, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate collaboration toolName");
    }

    @Test
    void rejectsHandoffModeInsideAgentTools() throws Exception {
        JsonNode manifest = manifestWithCollaboration("""
                "agentTools": [{
                  "targetAgentRef": "agent://reviewer/v1",
                  "mode": "HANDOFF",
                  "toolName": "review_result"
                }]
                """);

        assertThatThrownBy(() -> validator.validate(manifest, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("spec.collaboration.agentTools[0].mode must be AS_TOOL");
    }

    @Test
    void rejectsAsToolModeInsideHandoffs() throws Exception {
        JsonNode manifest = manifestWithCollaboration("""
                "handoffs": [{
                  "targetAgentRef": "agent://reviewer/v1",
                  "mode": "AS_TOOL"
                }]
                """);

        assertThatThrownBy(() -> validator.validate(manifest, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("spec.collaboration.handoffs[0].mode must be HANDOFF");
    }

    private JsonNode manifestWithCollaboration(String collaboration) throws Exception {
        return objectMapper.readTree("""
                {
                  "apiVersion": "ai.platform/v1alpha1",
                  "kind": "Agent",
                  "metadata": {"code": "manager", "name": "Manager"},
                  "spec": {
                    "instructions": {"type": "inline", "text": "Coordinate"},
                    "model": {"ref": "model://default"},
                    "collaboration": {%s}
                  }
                }
                """.formatted(collaboration));
    }
}
