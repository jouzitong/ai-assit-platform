package ai.platform.aiassit.user.security.management.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Id;
import lombok.Data;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.jdbc.entity.IEntity;

@Data
@TableName("sec_role")
public class SecRoleManagementEntity implements IEntity {

    @Id
    private Long id;

    @JdbcColumn(name = "role_code", comment = "角色编码")
    @TableField("role_code")
    private String roleCode;

    @JdbcColumn(name = "role_name", comment = "角色名称")
    @TableField("role_name")
    private String roleName;

    @JdbcColumn(name = "status", comment = "状态")
    @TableField("status")
    private String status;
}
