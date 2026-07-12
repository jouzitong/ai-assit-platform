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

    @JdbcColumn(name = "err_code", comment = "错误码")
    @TableField("err_code")
    private Integer errCode;

    @JdbcColumn(name = "locale", comment = "语言区域")
    @TableField("locale")
    private String locale;

    @JdbcColumn(name = "message_template", comment = "消息模板")
    @TableField("message_template")
    private String messageTemplate;

    @JdbcColumn(name = "description", comment = "描述")
    @TableField("description")
    private String description;
}
