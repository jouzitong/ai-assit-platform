package ai.platform.aiassit.user.security.management.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Id;
import lombok.Data;
import org.athena.framework.data.jdbc.entity.IEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

@Data
@TableName("sec_user_credential")
public class SecUserCredentialManagementEntity implements IEntity {

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
            name = "credential_type",
            dataType = "VARCHAR(255)",
            length = 255,
            nullable = true,
            comment = "凭证类型"
    )
    @TableField("credential_type")
    private String credentialType;

    @JdbcColumn(
            name = "password_hash",
            dataType = "VARCHAR(255)",
            length = 255,
            nullable = true,
            comment = "密码哈希"
    )
    @TableField("password_hash")
    private String passwordHash;

    @JdbcColumn(
            name = "password_algo",
            dataType = "VARCHAR(255)",
            length = 255,
            nullable = true,
            comment = "密码算法"
    )
    @TableField("password_algo")
    private String passwordAlgo;

    @JdbcColumn(
            name = "password_salt",
            dataType = "VARCHAR(255)",
            length = 255,
            nullable = true,
            comment = "密码盐"
    )
    @TableField("password_salt")
    private String passwordSalt;
}
