package ai.platform.aiassit.conversation.data.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.jdbc.annotations.JdbcIndex;
import org.athena.framework.data.jdbc.annotations.JdbcIndexType;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;

import java.time.LocalDateTime;

/** Reliable Provider delivery task. It stores locators and enums only, never message content. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("conversation_memory_sync_task")
@JdbcIndex(name = "uk_conversation_memory_sync_task_code", columnNames = "task_code", type = JdbcIndexType.UNIQUE)
@JdbcIndex(name = "uk_conversation_memory_sync_idempotency",
        columnNames = "idempotency_key", type = JdbcIndexType.UNIQUE)
@JdbcIndex(name = "idx_conversation_memory_sync_claim",
        columnNames = {"status", "next_retry_at", "id"})
@JdbcIndex(name = "idx_conversation_memory_sync_source",
        columnNames = {"tenant_id", "user_id", "session_code", "round_code"})
public class ConversationMemorySyncTaskEntity extends LogicalDeleteEntity {

    @JdbcColumn(name = "task_code", dataType = "VARCHAR(64)", length = 64, nullable = false,
            comment = "同步任务业务编码")
    @TableField("task_code")
    private String taskCode;

    @JdbcColumn(name = "tenant_id", dataType = "VARCHAR(128)", length = 128, nullable = false,
            comment = "平台可信租户标识")
    @TableField("tenant_id")
    private String tenantId;

    @JdbcColumn(name = "user_id", dataType = "BIGINT", nullable = false, comment = "平台可信用户ID")
    @TableField("user_id")
    private Long userId;

    @JdbcColumn(name = "session_code", dataType = "VARCHAR(64)", length = 64, nullable = false,
            comment = "权威会话定位")
    @TableField("session_code")
    private String sessionCode;

    @JdbcColumn(name = "round_code", dataType = "VARCHAR(64)", length = 64, nullable = true,
            comment = "权威轮次定位")
    @TableField("round_code")
    private String roundCode;

    @JdbcColumn(name = "target_scope", dataType = "VARCHAR(32)", length = 32, nullable = false,
            comment = "SESSION/LONG_TERM")
    @TableField("target_scope")
    private String targetScope;

    @JdbcColumn(name = "target_memory_id", dataType = "VARCHAR(128)", length = 128, nullable = true,
            comment = "目标Provider Memory ID")
    @TableField("target_memory_id")
    private String targetMemoryId;

    @JdbcColumn(name = "source_memory_id", dataType = "VARCHAR(128)", length = 128, nullable = true,
            comment = "来源Provider Memory ID，仅用于控制定位")
    @TableField("source_memory_id")
    private String sourceMemoryId;

    @JdbcColumn(name = "source_message_id", dataType = "VARCHAR(128)", length = 128, nullable = true,
            comment = "来源Provider消息ID，仅用于控制定位")
    @TableField("source_message_id")
    private String sourceMessageId;

    @JdbcColumn(name = "operation", dataType = "VARCHAR(32)", length = 32, nullable = false,
            comment = "ADD_ROUND/SET_STATUS/FORGET/PROMOTE/DELETE_SESSION/DELETE_MEMORY")
    @TableField("operation")
    private String operation;

    @JdbcColumn(name = "source_version", dataType = "BIGINT", nullable = false, defaultValue = "1",
            comment = "来源事实版本")
    @TableField("source_version")
    private Long sourceVersion;

    @JdbcColumn(name = "idempotency_key", dataType = "CHAR(64)", length = 64, nullable = false,
            comment = "本地幂等摘要")
    @TableField("idempotency_key")
    private String idempotencyKey;

    @JdbcColumn(name = "status", dataType = "VARCHAR(32)", length = 32, nullable = false,
            defaultValue = "'PENDING'", comment = "PENDING/PROCESSING/SUCCEEDED/RETRY/DEAD/UNKNOWN")
    @TableField("status")
    private String status;

    @JdbcColumn(name = "retry_count", dataType = "INT", nullable = false, defaultValue = "0",
            comment = "确定性失败重试次数")
    @TableField("retry_count")
    private Integer retryCount;

    @JdbcColumn(name = "next_retry_at", dataType = "DATETIME", nullable = true, comment = "下次重试时间")
    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;

    @JdbcColumn(name = "lease_until", dataType = "DATETIME", nullable = true, comment = "处理租约到期时间")
    @TableField("lease_until")
    private LocalDateTime leaseUntil;

    @JdbcColumn(name = "provider_message_id", dataType = "VARCHAR(128)", length = 128, nullable = true,
            comment = "Provider消息ID，可空")
    @TableField("provider_message_id")
    private String providerMessageId;

    @JdbcColumn(name = "last_error_code", dataType = "VARCHAR(64)", length = 64, nullable = true,
            comment = "脱敏稳定错误码")
    @TableField("last_error_code")
    private String lastErrorCode;

    @JdbcColumn(name = "finished_at", dataType = "DATETIME", nullable = true, comment = "任务完成时间")
    @TableField("finished_at")
    private LocalDateTime finishedAt;
}
