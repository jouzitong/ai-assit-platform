package ai.platform.aiassit.user.security.management.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Id;
import lombok.Data;
import org.athena.framework.data.jdbc.entity.IEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

/**
 * Athena Security 用户角色关联表映射。
 */
@Data
@TableName("sec_user_role")
public class SecUserRoleManagementEntity implements IEntity {

    @Id
    private Long id;

    @JdbcColumn(
            name = "user_id",
            dataType = "BIGINT",
            nullable = true,
            comment = "用户 ID"
    )
    @TableField("user_id")
    private Long userId;

    @JdbcColumn(
            name = "role_code",
            dataType = "VARCHAR(255)",
            length = 255,
            nullable = true,
            comment = "角色编码"
    )
    @TableField("role_code")
    private String roleCode;
}
