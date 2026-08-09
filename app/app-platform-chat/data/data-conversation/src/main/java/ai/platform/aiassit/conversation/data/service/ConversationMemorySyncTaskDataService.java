package ai.platform.aiassit.conversation.data.service;

import ai.platform.aiassit.conversation.data.entity.ConversationMemorySyncTaskEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface ConversationMemorySyncTaskDataService {
    ConversationMemorySyncTaskEntity insertIfAbsent(ConversationMemorySyncTaskEntity task);
    List<ConversationMemorySyncTaskEntity> findClaimable(LocalDateTime now, int limit);
    boolean claim(Long id, LocalDateTime now, LocalDateTime leaseUntil);
    int recoverExpiredLeases(LocalDateTime now);
    boolean hasOutstanding(String tenantId, Long userId, String sessionCode);
    List<ConversationMemorySyncTaskEntity> findOutstanding(
            String tenantId, Long userId, String sessionCode, String targetScope, int limit);
    boolean update(ConversationMemorySyncTaskEntity task);
}
