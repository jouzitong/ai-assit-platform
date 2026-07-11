package ai.platform.aiassit.user.security.management.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.athena.framework.data.jdbc.entity.IEntity;

/**
 * Athena Security 用户表映射。
 */
@Data
@TableName("sec_user")
public class SecUserManagementEntity implements IEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("username")
    private String username;

    @TableField("display_name")
    private String displayName;

    @TableField("status")
    private String status;

    @TableField("tenant_id")
    private String tenantId;
}
