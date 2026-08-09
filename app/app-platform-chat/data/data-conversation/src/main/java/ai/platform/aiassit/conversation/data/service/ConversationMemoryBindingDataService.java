package ai.platform.aiassit.conversation.data.service;

import ai.platform.aiassit.conversation.data.entity.ConversationMemoryBindingEntity;

import java.time.LocalDateTime;

public interface ConversationMemoryBindingDataService {
    ConversationMemoryBindingEntity find(String tenantId, Long userId, String providerType, String clientKey);
    ConversationMemoryBindingEntity insertIfAbsent(ConversationMemoryBindingEntity binding);
    boolean acquireProvisionLease(Long id, String owner, LocalDateTime now, LocalDateTime leaseUntil);
    boolean acquireLongTermMigrationLease(Long id, String oldMemoryId, String owner,
                                           LocalDateTime now, LocalDateTime leaseUntil);
    boolean switchLongTermMemory(Long id, String oldMemoryId, String newMemoryId,
                                 String owner, LocalDateTime verifiedAt);
    boolean abortLongTermMigration(Long id, String oldMemoryId, String owner);
    boolean clearRetiringLongTermMemory(Long id, String oldMemoryId);
    boolean update(ConversationMemoryBindingEntity binding);
}
