package ai.platform.aiassit.user.security.management.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.athena.framework.data.jdbc.entity.IEntity;

/**
 * Athena Security 用户角色关联表映射。
 */
@Data
@TableName("sec_user_role")
public class SecUserRoleManagementEntity implements IEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("role_code")
    private String roleCode;
}
