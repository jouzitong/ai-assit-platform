package ai.platform.aiassit.conversation.data.service.impl;

import ai.platform.aiassit.conversation.data.entity.ConversationMemorySessionPolicyEntity;
import ai.platform.aiassit.conversation.data.mapper.ConversationMemorySessionPolicyMapper;
import ai.platform.aiassit.conversation.data.service.ConversationMemorySessionPolicyDataService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ConversationMemorySessionPolicyDataServiceImpl implements ConversationMemorySessionPolicyDataService {

    private final ConversationMemorySessionPolicyMapper mapper;

    public ConversationMemorySessionPolicyDataServiceImpl(ConversationMemorySessionPolicyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ConversationMemorySessionPolicyEntity> findActive(
            String tenantId, Long userId, String sessionCode, LocalDateTime now) {
        if (tenantId == null || tenantId.isBlank() || userId == null || sessionCode == null || sessionCode.isBlank()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<ConversationMemorySessionPolicyEntity>lambdaQuery()
                .eq(ConversationMemorySessionPolicyEntity::getTenantId, tenantId.trim())
                .eq(ConversationMemorySessionPolicyEntity::getUserId, userId)
                .eq(ConversationMemorySessionPolicyEntity::getSessionCode, sessionCode.trim())
                .and(query -> query.isNull(ConversationMemorySessionPolicyEntity::getExpiresAt)
                        .or().gt(ConversationMemorySessionPolicyEntity::getExpiresAt, now)));
    }

    @Override
    public ConversationMemorySessionPolicyEntity insertIfAbsent(ConversationMemorySessionPolicyEntity policy) {
        try {
            mapper.insert(policy);
            return policy;
        } catch (DuplicateKeyException ex) {
            ConversationMemorySessionPolicyEntity existing = mapper.selectOne(
                    Wrappers.<ConversationMemorySessionPolicyEntity>lambdaQuery()
                            .eq(ConversationMemorySessionPolicyEntity::getTenantId, policy.getTenantId())
                            .eq(ConversationMemorySessionPolicyEntity::getUserId, policy.getUserId())
                            .eq(ConversationMemorySessionPolicyEntity::getSessionCode, policy.getSessionCode())
                            .eq(ConversationMemorySessionPolicyEntity::getProviderMemoryId, policy.getProviderMemoryId())
                            .eq(ConversationMemorySessionPolicyEntity::getProviderMessageId, policy.getProviderMessageId())
                            .eq(ConversationMemorySessionPolicyEntity::getAction, policy.getAction())
                            .last("LIMIT 1"));
            if (existing != null) {
                return existing;
            }
            throw ex;
        }
    }

    @Override
    public int deleteForMessage(String tenantId, Long userId, String providerMemoryId, String providerMessageId) {
        return mapper.delete(Wrappers.<ConversationMemorySessionPolicyEntity>lambdaQuery()
                .eq(ConversationMemorySessionPolicyEntity::getTenantId, tenantId)
                .eq(ConversationMemorySessionPolicyEntity::getUserId, userId)
                .eq(ConversationMemorySessionPolicyEntity::getProviderMemoryId, providerMemoryId)
                .eq(ConversationMemorySessionPolicyEntity::getProviderMessageId, providerMessageId));
    }

    @Override
    public int deleteForSession(String tenantId, Long userId, String sessionCode) {
        if (tenantId == null || tenantId.isBlank() || userId == null
                || sessionCode == null || sessionCode.isBlank()) {
            return 0;
        }
        return mapper.delete(Wrappers.<ConversationMemorySessionPolicyEntity>lambdaQuery()
                .eq(ConversationMemorySessionPolicyEntity::getTenantId, tenantId.trim())
                .eq(ConversationMemorySessionPolicyEntity::getUserId, userId)
                .eq(ConversationMemorySessionPolicyEntity::getSessionCode, sessionCode.trim()));
    }
}
