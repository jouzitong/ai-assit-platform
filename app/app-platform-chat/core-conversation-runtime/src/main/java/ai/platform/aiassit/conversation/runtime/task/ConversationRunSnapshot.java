package ai.platform.aiassit.conversation.runtime.task;

import java.time.Instant;

public record ConversationRunSnapshot(
        String runId,
        String ownerNodeId,
        String requestId,
        Long userId,
        String sessionCode,
        String roundCode,
        ConversationRunState state,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String error
) {
    public boolean active() {
        return state != null && state.isActive();
    }
}
