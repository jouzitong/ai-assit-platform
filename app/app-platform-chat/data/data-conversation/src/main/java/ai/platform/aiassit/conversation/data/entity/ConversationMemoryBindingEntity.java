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

/** Platform ownership binding to externally stored Memory resources; contains no memory text. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("conversation_memory_binding")
@JdbcIndex(name = "uk_conversation_memory_binding_code", columnNames = "binding_code", type = JdbcIndexType.UNIQUE)
@JdbcIndex(name = "uk_conversation_memory_binding_owner",
        columnNames = {"tenant_id", "user_id", "provider_type", "client_key"}, type = JdbcIndexType.UNIQUE)
@JdbcIndex(name = "uk_conversation_memory_binding_session_memory",
        columnNames = "session_memory_id", type = JdbcIndexType.UNIQUE)
@JdbcIndex(name = "uk_conversation_memory_binding_longterm_memory",
        columnNames = "long_term_memory_id", type = JdbcIndexType.UNIQUE)
public class ConversationMemoryBindingEntity extends LogicalDeleteEntity {

    @JdbcColumn(name = "binding_code", dataType = "VARCHAR(64)", length = 64, nullable = false,
            comment = "Memory绑定业务编码")
    @TableField("binding_code")
    private String bindingCode;

    @JdbcColumn(name = "tenant_id", dataType = "VARCHAR(128)", length = 128, nullable = false,
            comment = "平台可信租户标识")
    @TableField("tenant_id")
    private String tenantId;

    @JdbcColumn(name = "user_id", dataType = "BIGINT", nullable = false, comment = "平台可信用户ID")
    @TableField("user_id")
    private Long userId;

    @JdbcColumn(name = "provider_type", dataType = "VARCHAR(32)", length = 32, nullable = false,
            comment = "Memory Provider类型")
    @TableField("provider_type")
    private String providerType;

    @JdbcColumn(name = "client_key", dataType = "VARCHAR(64)", length = 64, nullable = false,
            comment = "系统参数中的Provider客户端key")
    @TableField("client_key")
    private String clientKey;

    @JdbcColumn(name = "session_memory_id", dataType = "VARCHAR(128)", length = 128, nullable = true,
            comment = "RAGFlow会话Memory ID")
    @TableField("session_memory_id")
    private String sessionMemoryId;

    @JdbcColumn(name = "long_term_memory_id", dataType = "VARCHAR(128)", length = 128, nullable = true,
            comment = "RAGFlow长期Memory ID")
    @TableField("long_term_memory_id")
    private String longTermMemoryId;

    @JdbcColumn(name = "retiring_long_term_memory_id", dataType = "VARCHAR(128)", length = 128,
            nullable = true, comment = "正在异步清理的旧RAGFlow长期Memory ID")
    @TableField("retiring_long_term_memory_id")
    private String retiringLongTermMemoryId;

    @JdbcColumn(name = "schema_version", dataType = "INT", nullable = false, defaultValue = "1",
            comment = "Provider Memory配置版本")
    @TableField("schema_version")
    private Integer schemaVersion;

    @JdbcColumn(name = "status", dataType = "VARCHAR(32)", length = 32, nullable = false,
            defaultValue = "'CREATING'", comment = "CREATING/ACTIVE/MIGRATING/DISABLED/FAILED")
    @TableField("status")
    private String status;

    @JdbcColumn(name = "last_verified_at", dataType = "DATETIME", nullable = true,
            comment = "最近Provider契约校验时间")
    @TableField("last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @JdbcColumn(name = "provision_owner", dataType = "VARCHAR(64)", length = 64, nullable = true,
            comment = "创建/补偿租约持有者")
    @TableField("provision_owner")
    private String provisionOwner;

    @JdbcColumn(name = "provision_lease_until", dataType = "DATETIME", nullable = true,
            comment = "创建/补偿租约到期时间")
    @TableField("provision_lease_until")
    private LocalDateTime provisionLeaseUntil;
}
