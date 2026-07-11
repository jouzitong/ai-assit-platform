package ai.platform.aiassit.user.security.management.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.athena.framework.data.jdbc.entity.IEntity;

@Data
@TableName("sec_user_credential")
public class SecUserCredentialManagementEntity implements IEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("credential_type")
    private String credentialType;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("password_algo")
    private String passwordAlgo;

    @TableField("password_salt")
    private String passwordSalt;
}
