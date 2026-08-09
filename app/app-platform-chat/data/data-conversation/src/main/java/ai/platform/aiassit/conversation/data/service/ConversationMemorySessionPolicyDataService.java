package ai.platform.aiassit.conversation.data.service;

import ai.platform.aiassit.conversation.data.entity.ConversationMemorySessionPolicyEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface ConversationMemorySessionPolicyDataService {
    List<ConversationMemorySessionPolicyEntity> findActive(
            String tenantId, Long userId, String sessionCode, LocalDateTime now);
    ConversationMemorySessionPolicyEntity insertIfAbsent(ConversationMemorySessionPolicyEntity policy);
    int deleteForMessage(String tenantId, Long userId, String providerMemoryId, String providerMessageId);
    int deleteForSession(String tenantId, Long userId, String sessionCode);
}
