package ai.platform.aiassit.service.ai.api.memory.enums;

/** User-facing lifecycle state derived from Provider state and local control tasks. */
public enum MemoryItemStatus {
    ACTIVE,
    DISABLED,
    PROCESSING,
    FAILED,
    FORGOTTEN
}
