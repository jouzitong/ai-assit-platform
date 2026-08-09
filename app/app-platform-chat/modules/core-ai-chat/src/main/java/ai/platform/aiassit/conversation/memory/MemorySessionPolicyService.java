package ai.platform.aiassit.conversation.memory;

import ai.platform.aiassit.conversation.data.entity.ConversationMemorySessionPolicyEntity;
import ai.platform.aiassit.conversation.data.service.ConversationMemorySessionPolicyDataService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MemorySessionPolicyService {

    private final ConversationMemorySessionPolicyDataService dataService;

    public MemorySessionPolicyService(ConversationMemorySessionPolicyDataService dataService) {
        this.dataService = dataService;
    }

    public Set<String> excludedMessageKeys(String tenantId, Long userId, String sessionCode) {
        return dataService.findActive(tenantId, userId, sessionCode, LocalDateTime.now()).stream()
                .filter(policy -> "EXCLUDE".equals(policy.getAction()))
                .map(this::key)
                .collect(Collectors.toSet());
    }

    public void exclude(String tenantId,
                        Long userId,
                        String sessionCode,
                        String providerMemoryId,
                        String providerMessageId) {
        ConversationMemorySessionPolicyEntity policy = new ConversationMemorySessionPolicyEntity();
        policy.setPolicyCode("memory-policy-" + UUID.randomUUID().toString().replace("-", ""));
        policy.setTenantId(tenantId);
        policy.setUserId(userId);
        policy.setSessionCode(sessionCode);
        policy.setProviderMemoryId(providerMemoryId);
        policy.setProviderMessageId(providerMessageId);
        policy.setAction("EXCLUDE");
        dataService.insertIfAbsent(policy);
    }

    public void removeForMessage(String tenantId,
                                 Long userId,
                                 String providerMemoryId,
                                 String providerMessageId) {
        dataService.deleteForMessage(tenantId, userId, providerMemoryId, providerMessageId);
    }

    public void removeForSession(String tenantId, Long userId, String sessionCode) {
        dataService.deleteForSession(tenantId, userId, sessionCode);
    }

    public String key(String memoryId, String messageId) {
        return (memoryId == null ? "" : memoryId) + "\u001f" + (messageId == null ? "" : messageId);
    }

    private String key(ConversationMemorySessionPolicyEntity policy) {
        return key(policy.getProviderMemoryId(), policy.getProviderMessageId());
    }
}
