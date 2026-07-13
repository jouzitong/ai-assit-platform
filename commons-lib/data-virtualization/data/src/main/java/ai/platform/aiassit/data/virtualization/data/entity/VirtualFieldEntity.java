package ai.platform.aiassit.data.virtualization.data.entity;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.LogicalType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "vd_field", autoResultMap = true)
public class VirtualFieldEntity extends AuditableEntity {
    @JdbcColumn(name = "entity_id", dataType = "BIGINT", nullable = false, comment = "虚拟实体ID")
    @TableField("entity_id")
    private Long entityId;

    @JdbcColumn(name = "field_code", dataType = "VARCHAR(64)", length = 64, nullable = false, comment = "虚拟字段稳定编码")
    @TableField("field_code")
    private String fieldCode;

    @JdbcColumn(name = "field_name", dataType = "VARCHAR(128)", length = 128, nullable = false, comment = "字段名称")
    @TableField("field_name")
    private String fieldName;

    @JdbcColumn(name = "logical_type", dataType = "INT", nullable = false, comment = "标准逻辑类型")
    @TableField(value = "logical_type", typeHandler = DefaultEnumTypeHandler.class)
    private LogicalType logicalType;

    @JdbcColumn(name = "nullable", dataType = "BOOLEAN", nullable = false, defaultValue = "TRUE", comment = "是否可空")
    @TableField("nullable")
    private Boolean nullable;

    @JdbcColumn(name = "primary_key", dataType = "BOOLEAN", nullable = false, defaultValue = "FALSE", comment = "是否虚拟主键")
    @TableField("primary_key")
    private Boolean primaryKey;

    @JdbcColumn(name = "ordinal_position", dataType = "INT", nullable = false, defaultValue = "0", comment = "字段顺序")
    @TableField("ordinal_position")
    private Integer ordinalPosition;

    @JdbcColumn(name = "default_value", dataType = "VARCHAR(512)", length = 512, nullable = true, comment = "默认值")
    @TableField("default_value")
    private String defaultValue;

    @JdbcColumn(name = "enabled", dataType = "BOOLEAN", nullable = false, defaultValue = "TRUE", comment = "是否启用")
    @TableField("enabled")
    private Boolean enabled;

    @JdbcColumn(name = "remark", dataType = "VARCHAR(512)", length = 512, nullable = true, comment = "备注")
    @TableField("remark")
    private String remark;
}
