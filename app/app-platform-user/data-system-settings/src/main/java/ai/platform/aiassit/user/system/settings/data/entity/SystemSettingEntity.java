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

    @JdbcColumn(
            name = "setting_key",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            unique = true,
            comment = "系统配置唯一键"
    )
    @TableField("setting_key")
    private String settingKey;

    @JdbcColumn(
            name = "description",
            dataType = "VARCHAR(512)",
            length = 512,
            nullable = true,
            comment = "配置说明"
    )
    @TableField("description")
    private String description;

    @JdbcColumn(
            name = "setting_value",
            dataType = "TEXT",
            nullable = true,
            comment = "配置值"
    )
    @TableField("setting_value")
    private String settingValue;

    @JdbcColumn(
            name = "value_type",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = false,
            defaultValue = "'STRING'",
            comment = "配置值类型"
    )
    @TableField("value_type")
    private String valueType;

    @JdbcColumn(
            name = "enabled",
            dataType = "BOOLEAN",
            nullable = false,
            defaultValue = "TRUE",
            comment = "是否启用"
    )
    @TableField("enabled")
    private Boolean enabled;
}
