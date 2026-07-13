package ai.platform.aiassit.data.virtualization.data.entity;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.LogicalType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "vd_field", autoResultMap = true)
public class VirtualFieldEntity extends AuditableEntity {
    @TableField("entity_id") private Long entityId;
    @TableField("field_code") private String fieldCode;
    @TableField("field_name") private String fieldName;
    @TableField(value = "logical_type", typeHandler = DefaultEnumTypeHandler.class) private LogicalType logicalType;
    @TableField("nullable") private Boolean nullable;
    @TableField("primary_key") private Boolean primaryKey;
    @TableField("ordinal_position") private Integer ordinalPosition;
    @TableField("default_value") private String defaultValue;
    @TableField("enabled") private Boolean enabled;
    @TableField("remark") private String remark;
}
