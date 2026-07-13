package ai.platform.aiassit.data.virtualization.data.entity;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FieldSide;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "vd_field_transform_port", autoResultMap = true)
public class FieldTransformPortEntity extends AuditableEntity {
    @JdbcColumn(name = "rule_id", dataType = "BIGINT", nullable = false, comment = "字段变换规则ID")
    @TableField("rule_id")
    private Long ruleId;

    @JdbcColumn(name = "field_side", dataType = "INT", nullable = false, comment = "字段侧：0物理字段，1虚拟字段")
    @TableField(value = "field_side", typeHandler = DefaultEnumTypeHandler.class)
    private FieldSide fieldSide;

    @JdbcColumn(name = "port_code", dataType = "VARCHAR(64)", length = 64, nullable = false, comment = "规则内端口编码")
    @TableField("port_code")
    private String portCode;

    @JdbcColumn(name = "virtual_field_id", dataType = "BIGINT", nullable = true, comment = "虚拟字段ID")
    @TableField("virtual_field_id")
    private Long virtualFieldId;

    @JdbcColumn(name = "physical_field_meta_id", dataType = "BIGINT", nullable = true, comment = "物理字段元数据ID")
    @TableField("physical_field_meta_id")
    private Long physicalFieldMetaId;

    @JdbcColumn(name = "physical_column_name", dataType = "VARCHAR(128)", length = 128, nullable = true, comment = "物理字段名快照")
    @TableField("physical_column_name")
    private String physicalColumnName;

    @JdbcColumn(name = "ordinal_position", dataType = "INT", nullable = false, defaultValue = "0", comment = "端口顺序")
    @TableField("ordinal_position")
    private Integer ordinalPosition;

    @JdbcColumn(name = "required_on_write", dataType = "BOOLEAN", nullable = false, defaultValue = "FALSE", comment = "写回时是否必填")
    @TableField("required_on_write")
    private Boolean requiredOnWrite;

    @JdbcColumn(name = "remark", dataType = "VARCHAR(512)", length = 512, nullable = true, comment = "备注")
    @TableField("remark")
    private String remark;
}
