package ai.platform.aiassit.user.errcode.data.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("err_code")
public class ErrCodeEntity extends AuditableEntity {

    @JdbcColumn(name = "code", unique = true, comment = "错误码")
    @TableField("code")
    private Integer code;

    @JdbcColumn(name = "http_status", comment = "HTTP 状态码")
    @TableField("http_status")
    private Integer httpStatus;

    @JdbcColumn(name = "description", comment = "描述")
    @TableField("description")
    private String description;

    @JdbcColumn(name = "tags", comment = "标签")
    @TableField("tags")
    private String tags;
}
