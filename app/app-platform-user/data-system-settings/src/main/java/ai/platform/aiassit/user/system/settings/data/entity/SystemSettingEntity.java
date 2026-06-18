package ai.platform.aiassit.user.system.settings.data.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.AuditableEntity;

/**
 * 系统配置实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("system_settings")
public class SystemSettingEntity extends AuditableEntity {

    @TableField("setting_key")
    private String settingKey;

    @TableField("description")
    private String description;

    @TableField("setting_value")
    private String settingValue;

    @TableField("value_type")
    private String valueType;

    @TableField("enabled")
    private Boolean enabled;
}
