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
@TableName("err_code_i18n")
public class ErrCodeI18nEntity extends AuditableEntity {

    @TableField("err_code")
    private Integer errCode;

    @TableField("locale")
    private String locale;

    @TableField("message_template")
    private String messageTemplate;

    @TableField("description")
    private String description;
}
