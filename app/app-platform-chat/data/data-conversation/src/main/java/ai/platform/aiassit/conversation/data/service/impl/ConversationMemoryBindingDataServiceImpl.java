package ai.platform.aiassit.conversation.data.service.impl;

import ai.platform.aiassit.conversation.data.entity.ConversationMemoryBindingEntity;
import ai.platform.aiassit.conversation.data.mapper.ConversationMemoryBindingMapper;
import ai.platform.aiassit.conversation.data.service.ConversationMemoryBindingDataService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ConversationMemoryBindingDataServiceImpl implements ConversationMemoryBindingDataService {

    private final ConversationMemoryBindingMapper mapper;

    public ConversationMemoryBindingDataServiceImpl(ConversationMemoryBindingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ConversationMemoryBindingEntity find(String tenantId, Long userId, String providerType, String clientKey) {
        if (tenantId == null || tenantId.isBlank() || userId == null
                || providerType == null || providerType.isBlank() || clientKey == null || clientKey.isBlank()) {
            return null;
        }
        return mapper.selectOne(Wrappers.<ConversationMemoryBindingEntity>lambdaQuery()
                .eq(ConversationMemoryBindingEntity::getTenantId, tenantId.trim())
                .eq(ConversationMemoryBindingEntity::getUserId, userId)
                .eq(ConversationMemoryBindingEntity::getProviderType, providerType.trim())
                .eq(ConversationMemoryBindingEntity::getClientKey, clientKey.trim())
                .last("LIMIT 1"));
    }

    @Override
    public ConversationMemoryBindingEntity insertIfAbsent(ConversationMemoryBindingEntity binding) {
        try {
            mapper.insert(binding);
            return binding;
        } catch (DuplicateKeyException ex) {
            ConversationMemoryBindingEntity existing = find(binding.getTenantId(), binding.getUserId(),
                    binding.getProviderType(), binding.getClientKey());
            if (existing != null) {
                return existing;
            }
            throw ex;
        }
    }

    @Override
    public boolean acquireProvisionLease(Long id, String owner, LocalDateTime now, LocalDateTime leaseUntil) {
        if (id == null || owner == null || owner.isBlank()) {
            return false;
        }
        return mapper.update(null, Wrappers.<ConversationMemoryBindingEntity>lambdaUpdate()
                .eq(ConversationMemoryBindingEntity::getId, id)
                .in(ConversationMemoryBindingEntity::getStatus, "CREATING", "FAILED")
                .and(query -> query.isNull(ConversationMemoryBindingEntity::getProvisionLeaseUntil)
                        .or().lt(ConversationMemoryBindingEntity::getProvisionLeaseUntil, now))
                .set(ConversationMemoryBindingEntity::getProvisionOwner, owner)
                .set(ConversationMemoryBindingEntity::getProvisionLeaseUntil, leaseUntil)
                .set(ConversationMemoryBindingEntity::getStatus, "CREATING")) == 1;
    }

    @Override
    public boolean acquireLongTermMigrationLease(Long id, String oldMemoryId, String owner,
                                                  LocalDateTime now, LocalDateTime leaseUntil) {
        if (id == null || oldMemoryId == null || oldMemoryId.isBlank() || owner == null || owner.isBlank()) {
            return false;
        }
        return mapper.update(null, Wrappers.<ConversationMemoryBindingEntity>lambdaUpdate()
                .eq(ConversationMemoryBindingEntity::getId, id)
                .eq(ConversationMemoryBindingEntity::getStatus, "ACTIVE")
                .eq(ConversationMemoryBindingEntity::getLongTermMemoryId, oldMemoryId)
                .and(query -> query.isNull(ConversationMemoryBindingEntity::getRetiringLongTermMemoryId)
                        .and(inner -> inner.isNull(ConversationMemoryBindingEntity::getProvisionLeaseUntil)
                                .or().lt(ConversationMemoryBindingEntity::getProvisionLeaseUntil, now)))
                .set(ConversationMemoryBindingEntity::getRetiringLongTermMemoryId, oldMemoryId)
                .set(ConversationMemoryBindingEntity::getProvisionOwner, owner)
                .set(ConversationMemoryBindingEntity::getProvisionLeaseUntil, leaseUntil)
                .set(ConversationMemoryBindingEntity::getStatus, "MIGRATING")) == 1;
    }

    @Override
    public boolean switchLongTermMemory(Long id, String oldMemoryId, String newMemoryId,
                                        String owner, LocalDateTime verifiedAt) {
        if (id == null || oldMemoryId == null || oldMemoryId.isBlank()
                || newMemoryId == null || newMemoryId.isBlank() || owner == null || owner.isBlank()) {
            return false;
        }
        return mapper.update(null, Wrappers.<ConversationMemoryBindingEntity>lambdaUpdate()
                .eq(ConversationMemoryBindingEntity::getId, id)
                .eq(ConversationMemoryBindingEntity::getStatus, "MIGRATING")
                .eq(ConversationMemoryBindingEntity::getLongTermMemoryId, oldMemoryId)
                .eq(ConversationMemoryBindingEntity::getRetiringLongTermMemoryId, oldMemoryId)
                .eq(ConversationMemoryBindingEntity::getProvisionOwner, owner)
                .set(ConversationMemoryBindingEntity::getLongTermMemoryId, newMemoryId)
                .set(ConversationMemoryBindingEntity::getStatus, "ACTIVE")
                .set(ConversationMemoryBindingEntity::getLastVerifiedAt, verifiedAt)
                .set(ConversationMemoryBindingEntity::getProvisionOwner, null)
                .set(ConversationMemoryBindingEntity::getProvisionLeaseUntil, null)) == 1;
    }

    @Override
    public boolean abortLongTermMigration(Long id, String oldMemoryId, String owner) {
        if (id == null || oldMemoryId == null || oldMemoryId.isBlank() || owner == null || owner.isBlank()) {
            return false;
        }
        return mapper.update(null, Wrappers.<ConversationMemoryBindingEntity>lambdaUpdate()
                .eq(ConversationMemoryBindingEntity::getId, id)
                .eq(ConversationMemoryBindingEntity::getStatus, "MIGRATING")
                .eq(ConversationMemoryBindingEntity::getLongTermMemoryId, oldMemoryId)
                .eq(ConversationMemoryBindingEntity::getRetiringLongTermMemoryId, oldMemoryId)
                .eq(ConversationMemoryBindingEntity::getProvisionOwner, owner)
                .set(ConversationMemoryBindingEntity::getRetiringLongTermMemoryId, null)
                .set(ConversationMemoryBindingEntity::getStatus, "ACTIVE")
                .set(ConversationMemoryBindingEntity::getProvisionOwner, null)
                .set(ConversationMemoryBindingEntity::getProvisionLeaseUntil, null)) == 1;
    }

    @Override
    public boolean clearRetiringLongTermMemory(Long id, String oldMemoryId) {
        if (id == null || oldMemoryId == null || oldMemoryId.isBlank()) {
            return false;
        }
        return mapper.update(null, Wrappers.<ConversationMemoryBindingEntity>lambdaUpdate()
                .eq(ConversationMemoryBindingEntity::getId, id)
                .eq(ConversationMemoryBindingEntity::getStatus, "ACTIVE")
                .eq(ConversationMemoryBindingEntity::getRetiringLongTermMemoryId, oldMemoryId)
                .set(ConversationMemoryBindingEntity::getRetiringLongTermMemoryId, null)) == 1;
    }

    @Override
    public boolean update(ConversationMemoryBindingEntity binding) {
        return binding != null && binding.getId() != null && mapper.updateById(binding) == 1;
    }
}
