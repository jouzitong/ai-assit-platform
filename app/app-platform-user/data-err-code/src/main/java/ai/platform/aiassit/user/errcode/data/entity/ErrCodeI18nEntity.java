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
@TableName("err_code_i18n")
public class ErrCodeI18nEntity extends AuditableEntity {

    @JdbcColumn(
            name = "err_code",
            dataType = "INT",
            nullable = false,
            comment = "错误码"
    )
    @TableField("err_code")
    private Integer errCode;

    @JdbcColumn(
            name = "locale",
            dataType = "VARCHAR(16)",
            length = 16,
            nullable = false,
            comment = "语言标识"
    )
    @TableField("locale")
    private String locale;

    @JdbcColumn(
            name = "message_template",
            dataType = "VARCHAR(1000)",
            length = 1000,
            nullable = true,
            comment = "错误消息模板"
    )
    @TableField("message_template")
    private String messageTemplate;

    @JdbcColumn(
            name = "description",
            dataType = "VARCHAR(512)",
            length = 512,
            nullable = true,
            comment = "当前语言说明"
    )
    @TableField("description")
    private String description;
}
