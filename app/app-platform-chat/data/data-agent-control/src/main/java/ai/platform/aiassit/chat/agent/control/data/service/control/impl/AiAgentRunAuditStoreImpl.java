package ai.platform.aiassit.chat.agent.control.data.service.control.impl;

import ai.platform.aiassit.chat.agent.control.data.entity.AiAgentRunEntity;
import ai.platform.aiassit.chat.agent.control.data.enums.AgentRuntimeType;
import ai.platform.aiassit.chat.agent.control.data.mapper.AiAgentRunMapper;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunAuditRecord;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunAuditStore;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.constant.ErrCodeConstant;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** MyBatis persistence adapter for runtime lifecycle auditing. */
@Slf4j
@Service
public class AiAgentRunAuditStoreImpl implements AgentRunAuditStore {

    private final AiAgentRunMapper mapper;

    public AiAgentRunAuditStoreImpl(AiAgentRunMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(AgentRunAuditRecord record) {
        validate(record);
        AiAgentRunEntity existing = find(record.getRunId());
        if (existing != null) {
            apply(existing, record);
            mapper.updateById(existing);
            log.debug("Agent run audit create was idempotently applied as update: runId={}, status={}",
                    record.getRunId(), record.getStatus());
            return;
        }
        AiAgentRunEntity entity = new AiAgentRunEntity();
        apply(entity, record);
        mapper.insert(entity);
        log.info("Agent run audit created: runId={}, agentCode={}, agentVersion={}, status={}, traceId={}",
                record.getRunId(), record.getRootAgentCode(), record.getRootAgentVersion(),
                record.getStatus(), record.getTraceId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(AgentRunAuditRecord record) {
        validate(record);
        AiAgentRunEntity entity = find(record.getRunId());
        if (entity == null) {
            create(record);
            return;
        }
        String previousStatus = entity.getStatus();
        apply(entity, record);
        mapper.updateById(entity);
        log.info("Agent run audit updated: runId={}, fromStatus={}, toStatus={}, traceId={}",
                record.getRunId(), previousStatus, record.getStatus(), record.getTraceId());
    }

    private AiAgentRunEntity find(String runId) {
        return mapper.selectOne(Wrappers.<AiAgentRunEntity>lambdaQuery()
                .eq(AiAgentRunEntity::getRunId, runId));
    }

    private void apply(AiAgentRunEntity entity, AgentRunAuditRecord record) {
        entity.setRunId(record.getRunId());
        entity.setSessionCode(trimToNull(record.getSessionCode()));
        entity.setRoundCode(trimToNull(record.getRoundCode()));
        entity.setRootAgentCode(record.getRootAgentCode().trim());
        entity.setRootAgentVersion(record.getRootAgentVersion());
        entity.setWorkflowCode(trimToNull(record.getWorkflowCode()));
        entity.setWorkflowVersion(record.getWorkflowVersion());
        entity.setRuntimeType(AgentRuntimeType.valueOf(record.getRuntimeType().name()));
        entity.setSdkVersion(trimToNull(record.getSdkVersion()));
        entity.setSnapshotHash(record.getSnapshotHash().trim());
        entity.setTraceId(trimToNull(record.getTraceId()));
        entity.setStatus(record.getStatus().trim());
        entity.setStartedAt(record.getStartedAt());
        entity.setFinishedAt(record.getFinishedAt());
        entity.setUsageJson(trimToNull(record.getUsageJson()));
        entity.setErrorSummary(truncate(trimToNull(record.getErrorSummary()), 1024));
    }

    private void validate(AgentRunAuditRecord record) {
        if (record == null || !StringUtils.hasText(record.getRunId())
                || !StringUtils.hasText(record.getRootAgentCode()) || record.getRootAgentVersion() == null
                || record.getRuntimeType() == null || !StringUtils.hasText(record.getSnapshotHash())
                || !StringUtils.hasText(record.getStatus())) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String truncate(String value, int maximum) {
        return value == null || value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
