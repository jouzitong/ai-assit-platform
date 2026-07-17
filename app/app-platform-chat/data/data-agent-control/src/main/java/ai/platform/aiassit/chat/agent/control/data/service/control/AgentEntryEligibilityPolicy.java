package ai.platform.aiassit.chat.agent.control.data.service.control;

import ai.platform.aiassit.chat.agent.control.data.entity.AiAgentVersionEntity;
import ai.platform.aiassit.chat.agent.control.data.enums.DefinitionStatus;
import ai.platform.aiassit.chat.agent.control.data.support.ControlPlaneJsonSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.util.Locale;
import java.util.Map;

/**
 * Determines whether a published Agent version explicitly declares support for a product entry.
 *
 * <p>The binding table is an operator-selected routing hint, not an authority boundary. Runtime
 * entry eligibility is owned by the immutable published manifest and therefore must be checked on
 * both control-plane writes and data-plane reads.</p>
 */
@Component
public class AgentEntryEligibilityPolicy {

    private final ControlPlaneJsonSupport json;

    public AgentEntryEligibilityPolicy(ControlPlaneJsonSupport json) {
        this.json = json;
    }

    public boolean supports(String entryCode, AiAgentVersionEntity version) {
        if (!StringUtils.hasText(entryCode)
                || version == null
                || version.getStatus() != DefinitionStatus.PUBLISHED
                || !StringUtils.hasText(version.getManifestJson())) {
            return false;
        }
        try {
            Map<String, Object> manifest = json.readMap(version.getManifestJson());
            Object metadataValue = manifest.get("metadata");
            if (!(metadataValue instanceof Map<?, ?> metadata)) {
                return false;
            }
            Object labelsValue = metadata.get("labels");
            if (!(labelsValue instanceof Map<?, ?> labels)) {
                return false;
            }
            return containsEntry(labels.get("entry"), normalize(entryCode));
        } catch (RuntimeException ignored) {
            // Persisted malformed manifests are never allowed to widen an entry authority boundary.
            return false;
        }
    }

    private boolean containsEntry(Object declaredEntries, String expectedEntry) {
        if (declaredEntries instanceof CharSequence value) {
            return expectedEntry.equals(normalize(value.toString()));
        }
        if (declaredEntries instanceof Iterable<?> values) {
            for (Object value : values) {
                if (containsEntry(value, expectedEntry)) {
                    return true;
                }
            }
            return false;
        }
        if (declaredEntries != null && declaredEntries.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(declaredEntries); index++) {
                if (containsEntry(Array.get(declaredEntries, index), expectedEntry)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
