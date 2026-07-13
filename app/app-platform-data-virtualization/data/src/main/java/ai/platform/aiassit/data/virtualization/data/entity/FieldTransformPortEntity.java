package ai.platform.aiassit.data.virtualization.data.entity;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FieldSide;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "vd_field_transform_port", autoResultMap = true)
public class FieldTransformPortEntity extends AuditableEntity {
    @TableField("rule_id") private Long ruleId;
    @TableField(value = "field_side", typeHandler = DefaultEnumTypeHandler.class) private FieldSide fieldSide;
    @TableField("port_code") private String portCode;
    @TableField("virtual_field_id") private Long virtualFieldId;
    @TableField("physical_field_meta_id") private Long physicalFieldMetaId;
    @TableField("physical_column_name") private String physicalColumnName;
    @TableField("ordinal_position") private Integer ordinalPosition;
    @TableField("required_on_write") private Boolean requiredOnWrite;
    @TableField("remark") private String remark;
}
