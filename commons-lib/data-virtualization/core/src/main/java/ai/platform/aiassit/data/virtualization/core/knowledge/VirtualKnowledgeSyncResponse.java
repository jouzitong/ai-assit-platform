package ai.platform.aiassit.data.virtualization.core.knowledge;

public record VirtualKnowledgeSyncResponse(
        String kbCode,
        int totalCount,
        int createdCount,
        int updatedCount,
        int unchangedCount
) {
}
