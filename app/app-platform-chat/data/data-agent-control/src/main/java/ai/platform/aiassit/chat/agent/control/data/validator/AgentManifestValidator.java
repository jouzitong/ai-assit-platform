package ai.platform.aiassit.chat.agent.control.data.validator;

import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.AgentControlDTOs;
import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.ValidationReportDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Structural validation for the language-neutral Agent manifest. */
@Component
public class AgentManifestValidator {

    private static final Set<String> FORBIDDEN_SECRET_KEYS = Set.of(
            "apikey", "token", "accesstoken", "refreshtoken", "password", "secret",
            "clientsecret", "authorization", "credential", "credentials", "cookie");

    public ValidationReportDTO validate(String agentCode, AgentControlDTOs.Manifest manifest) {
        ValidationReportDTO report = new ValidationReportDTO();
        if (manifest == null) {
            report.error("manifest is required");
            report.finish();
            return report;
        }
        if (!"Agent".equals(manifest.getKind())) {
            report.error("kind must be Agent");
        }
        if (!StringUtils.hasText(manifest.getApiVersion())) {
            report.error("apiVersion is required");
        } else if (!"ai.platform/v1alpha1".equals(manifest.getApiVersion())) {
            report.error("apiVersion must be ai.platform/v1alpha1");
        }
        if (manifest.getMetadata() == null) {
            report.error("metadata is required");
        } else {
            if (!StringUtils.hasText(manifest.getMetadata().getCode())) {
                report.error("metadata.code is required");
            } else if (StringUtils.hasText(agentCode)
                    && !agentCode.equals(manifest.getMetadata().getCode())) {
                report.error("metadata.code must match the Agent catalog code");
            }
            if (manifest.getMetadata().getVersion() == null || manifest.getMetadata().getVersion() < 1) {
                report.error("metadata.version must be positive");
            }
            if (!StringUtils.hasText(manifest.getMetadata().getName())) {
                report.error("metadata.name is required");
            }
        }
        AgentControlDTOs.Spec spec = manifest.getSpec();
        if (spec == null) {
            report.error("spec is required");
            report.finish();
            return report;
        }
        AgentControlDTOs.Instructions instructions = spec.getInstructions();
        if (instructions == null || !StringUtils.hasText(instructions.getType())) {
            report.error("spec.instructions.type is required");
        } else if (!"inline".equalsIgnoreCase(instructions.getType())) {
            report.error("spec.instructions.type must be inline until promptRef resolution is configured");
        } else if (!StringUtils.hasText(instructions.getText())) {
            report.error("spec.instructions.text is required for inline instructions");
        }
        if (spec.getModel() == null || !StringUtils.hasText(spec.getModel().getRef())) {
            report.error("model.ref is required");
        }
        validateCapabilityRefs("spec.skillRefs", spec.getSkillRefs(), report);
        validateCapabilityRefs("spec.toolRefs", spec.getToolRefs(), report);
        validateCapabilityRefs("spec.knowledgeRefs", spec.getKnowledgeRefs(), report);
        validateCapabilityRefs("spec.mcpRefs", spec.getMcpRefs(), report);
        validateCollaborators(agentCode, spec.getCollaboration(), report);
        validateRuntimePolicy(spec.getRuntimeDefaults(), report);
        rejectEmbeddedSecrets(manifest.getMetadata() == null ? null : manifest.getMetadata().getLabels(),
                "metadata.labels", report);
        rejectEmbeddedSecrets(spec.getModel() == null ? null : spec.getModel().getSettings(),
                "spec.model.settings", report);
        rejectEmbeddedSecrets(spec.getOutput() == null ? null : spec.getOutput().getSchema(),
                "spec.output.schema", report);
        rejectEmbeddedSecrets(spec.getExtensions(), "spec.extensions", report);
        report.finish();
        return report;
    }

    private void validateCapabilityRefs(String field,
                                        List<AgentControlDTOs.CapabilityRef> refs,
                                        ValidationReportDTO report) {
        if (refs == null) {
            return;
        }
        Set<String> unique = new HashSet<>();
        for (int index = 0; index < refs.size(); index++) {
            AgentControlDTOs.CapabilityRef ref = refs.get(index);
            if (ref == null || !StringUtils.hasText(ref.getRef())) {
                report.error(field + "[" + index + "].ref is required");
                continue;
            }
            String key = ref.getRef().trim().toLowerCase(Locale.ROOT) + ":" + ref.getVersion();
            if (!unique.add(key)) {
                report.error(field + " contains duplicate reference: " + ref.getRef());
            }
            if (ref.getVersion() != null && ref.getVersion() < 1) {
                report.error(field + " version must be positive: " + ref.getRef());
            }
        }
    }

    private void validateCollaborators(String agentCode,
                                       AgentControlDTOs.Collaboration collaboration,
                                       ValidationReportDTO report) {
        if (collaboration == null) {
            return;
        }
        Set<String> unique = new HashSet<>();
        validateCollaboratorGroup(agentCode, "spec.collaboration.agentTools",
                collaboration.getAgentTools(), "AS_TOOL", unique, report);
        validateCollaboratorGroup(agentCode, "spec.collaboration.handoffs",
                collaboration.getHandoffs(), "HANDOFF", unique, report);
    }

    private void validateCollaboratorGroup(String agentCode,
                                           String field,
                                           List<AgentControlDTOs.CollaboratorRef> refs,
                                           String requiredMode,
                                           Set<String> unique,
                                           ValidationReportDTO report) {
        if (refs == null) {
            return;
        }
        for (int index = 0; index < refs.size(); index++) {
            AgentControlDTOs.CollaboratorRef ref = refs.get(index);
            if (ref == null || !StringUtils.hasText(ref.getTargetAgentRef())) {
                report.error(field + "[" + index + "].targetAgentRef is required");
                continue;
            }
            String targetCode = referenceCode(ref.getTargetAgentRef());
            if (agentCode != null && agentCode.equalsIgnoreCase(targetCode)) {
                report.error("an Agent cannot directly collaborate with itself");
            }
            String mode = normalize(ref.getMode());
            if (!requiredMode.equals(mode)) {
                report.error(field + "[" + index + "].mode must be " + requiredMode);
            }
            String key = ref.getTargetAgentRef().trim().toLowerCase(Locale.ROOT) + ":" + mode;
            if (!unique.add(key)) {
                report.error("collaboration contains duplicate relationship: " + ref.getTargetAgentRef() + "/" + mode);
            }
            if ("AS_TOOL".equals(requiredMode) && !StringUtils.hasText(ref.getToolName())) {
                report.error("AS_TOOL collaborator requires toolName");
            }
        }
    }

    private void validateRuntimePolicy(AgentControlDTOs.RuntimeDefaults policy, ValidationReportDTO report) {
        if (policy == null) {
            return;
        }
        validateRange("runtimeDefaults.maxTurns", policy.getMaxTurns(), 1, 50, report);
        validateRange("runtimeDefaults.timeoutMs", policy.getTimeoutMs(), 1_000, 600_000, report);
        validateRange("runtimeDefaults.toolConcurrency", policy.getToolConcurrency(), 1, 32, report);
        validateRange("runtimeDefaults.maxAgentDepth", policy.getMaxAgentDepth(), 1, 4, report);
        if (!"applicationReplay".equals(policy.getStateStrategy())) {
            report.error("runtimeDefaults.stateStrategy must be applicationReplay");
        }
        if (policy.getTracing() != null && Boolean.TRUE.equals(policy.getTracing().getIncludeSensitiveData())) {
            report.error("runtimeDefaults.tracing.includeSensitiveData must be false");
        }
    }

    private void rejectEmbeddedSecrets(Object value, String path, ValidationReportDTO report) {
        if (value instanceof Map<?, ?> values) {
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                Object child = entry.getValue();
                if (FORBIDDEN_SECRET_KEYS.contains(normalized) && hasValue(child)) {
                    report.error("Agent manifest must not contain secret field: " + path + "." + key);
                }
                rejectEmbeddedSecrets(child, path + "." + key, report);
            }
        } else if (value instanceof Iterable<?> values) {
            int index = 0;
            for (Object child : values) rejectEmbeddedSecrets(child, path + "[" + index++ + "]", report);
        }
    }

    private boolean hasValue(Object value) {
        if (value == null) return false;
        if (value instanceof String text) return StringUtils.hasText(text);
        if (value instanceof Map<?, ?> values) return !values.isEmpty();
        if (value instanceof Iterable<?> values) return values.iterator().hasNext();
        return true;
    }

    private void validateRange(String field, Integer value, int minimum, int maximum,
                               ValidationReportDTO report) {
        if (value != null && (value < minimum || value > maximum)) {
            report.error(field + " must be between " + minimum + " and " + maximum);
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String referenceCode(String reference) {
        String value = reference == null ? "" : reference.trim();
        int scheme = value.indexOf("://");
        if (scheme >= 0) value = value.substring(scheme + 3);
        return value.replaceFirst("/v\\d+$", "");
    }
}
