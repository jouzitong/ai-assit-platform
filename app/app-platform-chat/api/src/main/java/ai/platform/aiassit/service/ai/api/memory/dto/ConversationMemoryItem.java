package ai.platform.aiassit.service.ai.api.memory.dto;

import ai.platform.aiassit.service.ai.api.memory.enums.MemoryItemStatus;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryScope;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryType;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/**
 * Safe UI projection of a Provider-owned memory.
 *
 * <p>The reference is opaque and Provider identifiers are deliberately absent.</p>
 */
@Data
public class ConversationMemoryItem implements Serializable {
    private String memoryRef;
    private MemoryScope scope;
    private MemoryType memoryType;
    private MemoryItemStatus status;
    private String content;
    private String sourceSessionCode;
    private String sourceRoundCode;
    private Instant createdAt;
    private boolean excludedFromSession;
}
