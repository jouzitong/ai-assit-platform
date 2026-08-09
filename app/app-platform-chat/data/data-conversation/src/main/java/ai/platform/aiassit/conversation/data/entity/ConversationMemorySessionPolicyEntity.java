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

/** Session-local use policy referencing Provider IDs only; contains no copied memory text. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("conversation_memory_session_policy")
@JdbcIndex(name = "uk_conversation_memory_policy_code", columnNames = "policy_code", type = JdbcIndexType.UNIQUE)
@JdbcIndex(name = "uk_conversation_memory_policy_target",
        columnNames = {"tenant_id", "user_id", "session_code", "provider_memory_id", "provider_message_id", "action"},
        type = JdbcIndexType.UNIQUE)
@JdbcIndex(name = "idx_conversation_memory_policy_owner",
        columnNames = {"tenant_id", "user_id", "session_code"})
public class ConversationMemorySessionPolicyEntity extends LogicalDeleteEntity {

    @JdbcColumn(name = "policy_code", dataType = "VARCHAR(64)", length = 64, nullable = false,
            comment = "会话策略业务编码")
    @TableField("policy_code")
    private String policyCode;

    @JdbcColumn(name = "tenant_id", dataType = "VARCHAR(128)", length = 128, nullable = false,
            comment = "平台可信租户标识")
    @TableField("tenant_id")
    private String tenantId;

    @JdbcColumn(name = "user_id", dataType = "BIGINT", nullable = false, comment = "平台可信用户ID")
    @TableField("user_id")
    private Long userId;

    @JdbcColumn(name = "session_code", dataType = "VARCHAR(64)", length = 64, nullable = false,
            comment = "策略生效会话")
    @TableField("session_code")
    private String sessionCode;

    @JdbcColumn(name = "provider_memory_id", dataType = "VARCHAR(128)", length = 128, nullable = false,
            comment = "Provider Memory ID")
    @TableField("provider_memory_id")
    private String providerMemoryId;

    @JdbcColumn(name = "provider_message_id", dataType = "VARCHAR(128)", length = 128, nullable = false,
            comment = "Provider消息ID")
    @TableField("provider_message_id")
    private String providerMessageId;

    @JdbcColumn(name = "action", dataType = "VARCHAR(16)", length = 16, nullable = false,
            comment = "EXCLUDE/PIN")
    @TableField("action")
    private String action;

    @JdbcColumn(name = "expires_at", dataType = "DATETIME", nullable = true, comment = "可选失效时间")
    @TableField("expires_at")
    private LocalDateTime expiresAt;
}
