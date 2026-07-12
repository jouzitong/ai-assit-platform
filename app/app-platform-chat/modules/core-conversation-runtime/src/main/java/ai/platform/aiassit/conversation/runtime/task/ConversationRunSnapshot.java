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

    /**
     * 日志摘要：仅输出运行任务的定位与状态信息，不输出异常原文。
     */
    @Override
    public String toString() {
        return "ConversationRunSnapshot{" +
                "runId='" + runId + '\'' +
                ", requestId='" + requestId + '\'' +
                ", userId=" + userId +
                ", sessionCode='" + sessionCode + '\'' +
                ", roundCode='" + roundCode + '\'' +
                ", ownerNodeId='" + ownerNodeId + '\'' +
                ", state=" + state +
                ", createdAt=" + createdAt +
                ", startedAt=" + startedAt +
                ", finishedAt=" + finishedAt +
                '}';
    }
}
