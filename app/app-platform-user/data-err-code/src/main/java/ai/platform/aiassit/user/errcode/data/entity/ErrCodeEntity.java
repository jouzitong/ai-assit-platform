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

    @JdbcColumn(
            name = "code",
            dataType = "INT",
            nullable = false,
            unique = true,
            comment = "错误码"
    )
    @TableField("code")
    private Integer code;

    @JdbcColumn(
            name = "http_status",
            dataType = "INT",
            nullable = false,
            defaultValue = "200",
            comment = "HTTP状态码"
    )
    @TableField("http_status")
    private Integer httpStatus;

    @JdbcColumn(
            name = "description",
            dataType = "VARCHAR(512)",
            length = 512,
            nullable = true,
            comment = "错误说明"
    )
    @TableField("description")
    private String description;

    @JdbcColumn(
            name = "tags",
            dataType = "VARCHAR(255)",
            length = 255,
            nullable = true,
            comment = "标签，逗号分隔"
    )
    @TableField("tags")
    private String tags;
}
