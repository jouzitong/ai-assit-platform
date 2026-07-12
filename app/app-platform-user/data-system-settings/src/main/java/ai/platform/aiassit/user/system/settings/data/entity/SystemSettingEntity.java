package ai.platform.aiassit.user.system.settings.data.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

/**
 * 系统配置实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("system_settings")
public class SystemSettingEntity extends AuditableEntity {

    @JdbcColumn(name = "setting_key", unique = true, comment = "配置键")
    @TableField("setting_key")
    private String settingKey;

    @JdbcColumn(name = "description", comment = "描述")
    @TableField("description")
    private String description;

    @JdbcColumn(name = "setting_value", comment = "配置值")
    @TableField("setting_value")
    private String settingValue;

    @JdbcColumn(name = "value_type", comment = "值类型")
    @TableField("value_type")
    private String valueType;

    @JdbcColumn(name = "enabled", comment = "是否启用")
    @TableField("enabled")
    private Boolean enabled;
}
