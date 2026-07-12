package ai.platform.aiassit.user.security.management.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Id;
import lombok.Data;
import org.athena.framework.data.jdbc.entity.IEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

/**
 * Athena Security 用户表映射。
 */
@Data
@TableName("sec_user")
public class SecUserManagementEntity implements IEntity {

    @Id
    private Long id;

    @JdbcColumn(name = "username", comment = "用户名")
    @TableField("username")
    private String username;

    @JdbcColumn(name = "display_name", comment = "显示名称")
    @TableField("display_name")
    private String displayName;

    @JdbcColumn(name = "status", comment = "状态")
    @TableField("status")
    private String status;

    @JdbcColumn(name = "tenant_id", comment = "租户 ID")
    @TableField("tenant_id")
    private String tenantId;
}
