package ai.platform.aiassit.conversation.data.service.impl;

import ai.platform.aiassit.conversation.data.entity.ConversationMemorySyncTaskEntity;
import ai.platform.aiassit.conversation.data.mapper.ConversationMemorySyncTaskMapper;
import ai.platform.aiassit.conversation.data.service.ConversationMemorySyncTaskDataService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ConversationMemorySyncTaskDataServiceImpl implements ConversationMemorySyncTaskDataService {

    /** UNKNOWN is claimable only for reconciliation; the Worker decides whether it may retry. */
    private static final List<String> CLAIMABLE = List.of("PENDING", "RETRY", "UNKNOWN");
    private final ConversationMemorySyncTaskMapper mapper;

    public ConversationMemorySyncTaskDataServiceImpl(ConversationMemorySyncTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ConversationMemorySyncTaskEntity insertIfAbsent(ConversationMemorySyncTaskEntity task) {
        try {
            mapper.insert(task);
            return task;
        } catch (DuplicateKeyException ex) {
            ConversationMemorySyncTaskEntity existing = mapper.selectOne(
                    Wrappers.<ConversationMemorySyncTaskEntity>lambdaQuery()
                            .eq(ConversationMemorySyncTaskEntity::getIdempotencyKey, task.getIdempotencyKey())
                            .last("LIMIT 1"));
            if (existing != null) {
                return existing;
            }
            throw ex;
        }
    }

    @Override
    public List<ConversationMemorySyncTaskEntity> findClaimable(LocalDateTime now, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return mapper.selectList(Wrappers.<ConversationMemorySyncTaskEntity>lambdaQuery()
                .in(ConversationMemorySyncTaskEntity::getStatus, CLAIMABLE)
                .and(query -> query.isNull(ConversationMemorySyncTaskEntity::getNextRetryAt)
                        .or().le(ConversationMemorySyncTaskEntity::getNextRetryAt, now))
                .orderByAsc(ConversationMemorySyncTaskEntity::getId)
                .last("LIMIT " + safeLimit));
    }

    @Override
    public boolean claim(Long id, LocalDateTime now, LocalDateTime leaseUntil) {
        if (id == null) {
            return false;
        }
        return mapper.update(null, Wrappers.<ConversationMemorySyncTaskEntity>lambdaUpdate()
                .eq(ConversationMemorySyncTaskEntity::getId, id)
                .in(ConversationMemorySyncTaskEntity::getStatus, CLAIMABLE)
                .and(query -> query.isNull(ConversationMemorySyncTaskEntity::getNextRetryAt)
                        .or().le(ConversationMemorySyncTaskEntity::getNextRetryAt, now))
                .set(ConversationMemorySyncTaskEntity::getStatus, "PROCESSING")
                .set(ConversationMemorySyncTaskEntity::getLeaseUntil, leaseUntil)) == 1;
    }

    @Override
    public int recoverExpiredLeases(LocalDateTime now) {
        return mapper.update(null, Wrappers.<ConversationMemorySyncTaskEntity>lambdaUpdate()
                .eq(ConversationMemorySyncTaskEntity::getStatus, "PROCESSING")
                .lt(ConversationMemorySyncTaskEntity::getLeaseUntil, now)
                // An expired ADD_ROUND lease may have been accepted by RAGFlow. Never replay it blindly.
                .set(ConversationMemorySyncTaskEntity::getStatus, "UNKNOWN")
                .set(ConversationMemorySyncTaskEntity::getNextRetryAt, null)
                .set(ConversationMemorySyncTaskEntity::getLeaseUntil, null)
                .set(ConversationMemorySyncTaskEntity::getLastErrorCode, "MEMORY_SYNC_OUTCOME_UNKNOWN"));
    }

    @Override
    public boolean hasOutstanding(String tenantId, Long userId, String sessionCode) {
        if (tenantId == null || tenantId.isBlank() || userId == null || sessionCode == null || sessionCode.isBlank()) {
            return false;
        }
        return mapper.selectCount(Wrappers.<ConversationMemorySyncTaskEntity>lambdaQuery()
                .eq(ConversationMemorySyncTaskEntity::getTenantId, tenantId.trim())
                .eq(ConversationMemorySyncTaskEntity::getUserId, userId)
                .eq(ConversationMemorySyncTaskEntity::getSessionCode, sessionCode.trim())
                .in(ConversationMemorySyncTaskEntity::getStatus,
                        "PENDING", "PROCESSING", "RETRY", "UNKNOWN")) > 0;
    }

    @Override
    public List<ConversationMemorySyncTaskEntity> findOutstanding(
            String tenantId, Long userId, String sessionCode, String targetScope, int limit) {
        if (tenantId == null || tenantId.isBlank() || userId == null) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        var query = Wrappers.<ConversationMemorySyncTaskEntity>lambdaQuery()
                .eq(ConversationMemorySyncTaskEntity::getTenantId, tenantId.trim())
                .eq(ConversationMemorySyncTaskEntity::getUserId, userId)
                .in(ConversationMemorySyncTaskEntity::getStatus,
                        "PENDING", "PROCESSING", "RETRY", "UNKNOWN");
        if (sessionCode != null && !sessionCode.isBlank()) {
            query.eq(ConversationMemorySyncTaskEntity::getSessionCode, sessionCode.trim());
        }
        if (targetScope != null && !targetScope.isBlank()) {
            query.eq(ConversationMemorySyncTaskEntity::getTargetScope, targetScope.trim());
        }
        return mapper.selectList(query.orderByDesc(ConversationMemorySyncTaskEntity::getId)
                .last("LIMIT " + safeLimit));
    }

    @Override
    public boolean update(ConversationMemorySyncTaskEntity task) {
        return task != null && task.getId() != null && mapper.updateById(task) == 1;
    }
}
