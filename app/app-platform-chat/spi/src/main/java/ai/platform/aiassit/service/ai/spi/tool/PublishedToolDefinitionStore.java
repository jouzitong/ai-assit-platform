package ai.platform.aiassit.service.ai.spi.tool;

import java.util.Optional;

/** Read-only run-plane lookup; implementations must return published versions only. */
public interface PublishedToolDefinitionStore {
    Optional<PublishedToolDefinition> findPublished(String toolCode, Integer toolVersion);
}
