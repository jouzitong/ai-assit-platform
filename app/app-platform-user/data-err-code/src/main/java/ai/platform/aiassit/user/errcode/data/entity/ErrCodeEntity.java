package ai.platform.aiassit.user.errcode.data.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.AuditableEntity;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("err_code")
public class ErrCodeEntity extends AuditableEntity {

    @TableField("code")
    private Integer code;

    @TableField("http_status")
    private Integer httpStatus;

    @TableField("description")
    private String description;

    @TableField("tags")
    private String tags;
}
