package ai.platform.aiassit.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Strict validation for the portable subset accepted by both SDK adapters. */
@Component
public class AgentManifestValidator {

    private static final Set<String> FORBIDDEN_SECRET_KEYS = Set.of(
            "apikey", "token", "accesstoken", "refreshtoken", "password", "secret",
            "clientsecret", "authorization", "credential", "credentials", "cookie"
    );

    public void validate(JsonNode manifest, String expectedCode) {
        if (manifest == null || !manifest.isObject()) {
            throw new IllegalArgumentException("Agent manifest must be a JSON object");
        }
        requireText(manifest, "/apiVersion", "apiVersion");
        String kind = requireText(manifest, "/kind", "kind");
        if (!"Agent".equals(kind)) {
            throw new IllegalArgumentException("Agent manifest kind must be Agent");
        }
        String code = requireText(manifest, "/metadata/code", "metadata.code");
        if (StringUtils.hasText(expectedCode) && !expectedCode.equals(code)) {
            throw new IllegalArgumentException("Agent manifest code does not match the published identity");
        }
        requireText(manifest, "/metadata/name", "metadata.name");
        requireText(manifest, "/spec/instructions/text", "spec.instructions.text");
        requireText(manifest, "/spec/model/ref", "spec.model.ref");
        validateRuntimeDefaults(manifest.at("/spec/runtimeDefaults"));
        validateCollaboration(manifest.at("/spec/collaboration"));
        rejectEmbeddedSecrets(manifest);
    }

    private void validateRuntimeDefaults(JsonNode defaults) {
        if (defaults == null || defaults.isMissingNode() || defaults.isNull()) {
            return;
        }
        int maxTurns = defaults.path("maxTurns").asInt(12);
        int maxDepth = defaults.path("maxAgentDepth").asInt(4);
        int timeoutMs = defaults.path("timeoutMs").asInt(120_000);
        if (maxTurns < 1 || maxTurns > 50) {
            throw new IllegalArgumentException("runtimeDefaults.maxTurns must be between 1 and 50");
        }
        if (maxDepth < 1 || maxDepth > 4) {
            throw new IllegalArgumentException("runtimeDefaults.maxAgentDepth must be between 1 and 4");
        }
        if (timeoutMs < 1_000 || timeoutMs > 600_000) {
            throw new IllegalArgumentException("runtimeDefaults.timeoutMs must be between 1000 and 600000");
        }
    }

    private void validateCollaboration(JsonNode collaboration) {
        if (collaboration == null || collaboration.isMissingNode() || collaboration.isNull()) {
            return;
        }
        Set<String> names = new HashSet<>();
        validateCollaborators(collaboration.path("agentTools"), names, true);
        validateCollaborators(collaboration.path("handoffs"), names, false);
    }

    private void validateCollaborators(JsonNode refs, Set<String> names, boolean requireToolName) {
        if (refs == null || refs.isMissingNode() || refs.isNull()) {
            return;
        }
        if (!refs.isArray()) {
            throw new IllegalArgumentException("Agent collaboration entries must be arrays");
        }
        for (JsonNode ref : refs) {
            requireText(ref, "/targetAgentRef", "targetAgentRef");
            String toolName = ref.path("toolName").asText(null);
            if (requireToolName && !StringUtils.hasText(toolName)) {
                throw new IllegalArgumentException("agent-as-tool requires toolName");
            }
            if (StringUtils.hasText(toolName) && !names.add(toolName)) {
                throw new IllegalArgumentException("Duplicate collaboration toolName: " + toolName);
            }
        }
    }

    private void rejectEmbeddedSecrets(JsonNode node) {
        if (node == null || !node.isContainerNode()) {
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String normalized = field.getKey().toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]", "");
                if (FORBIDDEN_SECRET_KEYS.contains(normalized)
                        && !field.getValue().isNull()
                        && StringUtils.hasText(field.getValue().asText())) {
                    throw new IllegalArgumentException("Agent manifest must not contain secret field: " + field.getKey());
                }
                rejectEmbeddedSecrets(field.getValue());
            }
            return;
        }
        for (JsonNode child : node) {
            rejectEmbeddedSecrets(child);
        }
    }

    private String requireText(JsonNode root, String pointer, String label) {
        JsonNode value = root.at(pointer);
        if (value == null || !value.isTextual() || !StringUtils.hasText(value.asText())) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.asText().trim();
    }
}
