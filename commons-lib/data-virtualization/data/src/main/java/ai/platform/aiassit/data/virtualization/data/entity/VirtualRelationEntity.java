package ai.platform.aiassit.data.virtualization.data.entity;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.RelationResultMode;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.AuditableEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vd_relation")
public class VirtualRelationEntity extends AuditableEntity {
    @JdbcColumn(name = "relation_code", dataType = "VARCHAR(64)", length = 64, nullable = false, comment = "源实体内关系编码")
    @TableField("relation_code")
    private String relationCode;

    @JdbcColumn(name = "relation_name", dataType = "VARCHAR(128)", length = 128, nullable = false, comment = "关系名称")
    @TableField("relation_name")
    private String relationName;

    /** 当前源虚拟实体中该关系的返回形态，不从物理表推断基数。 */
    @JdbcColumn(name = "relation_result_mode", dataType = "TINYINT", nullable = false, defaultValue = "0", comment = "关联结果形态：0=对象，1=集合")
    @TableField("relation_result_mode")
    private RelationResultMode resultMode = RelationResultMode.OBJECT;

    /** 从目标虚拟实体反向访问源虚拟实体时的返回形态；为空时由目录根据源虚拟主键保守推导。 */
    @JdbcColumn(name = "reverse_result_mode", dataType = "TINYINT", nullable = true, comment = "反向关联结果形态：0=对象，1=集合")
    @TableField("reverse_result_mode")
    private RelationResultMode reverseResultMode;

    @JdbcColumn(name = "source_entity_id", dataType = "BIGINT", nullable = false, comment = "源虚拟实体ID")
    @TableField("source_entity_id")
    private Long sourceEntityId;

    @JdbcColumn(name = "source_field_id", dataType = "BIGINT", nullable = false, comment = "源虚拟字段ID")
    @TableField("source_field_id")
    private Long sourceFieldId;

    @JdbcColumn(name = "target_entity_id", dataType = "BIGINT", nullable = false, comment = "目标虚拟实体ID")
    @TableField("target_entity_id")
    private Long targetEntityId;

    @JdbcColumn(name = "target_field_id", dataType = "BIGINT", nullable = false, comment = "目标虚拟字段ID")
    @TableField("target_field_id")
    private Long targetFieldId;

    @JdbcColumn(name = "enabled", dataType = "BOOLEAN", nullable = false, defaultValue = "TRUE", comment = "是否启用")
    @TableField("enabled")
    private Boolean enabled;

    @JdbcColumn(name = "remark", dataType = "VARCHAR(512)", length = 512, nullable = true, comment = "备注")
    @TableField("remark")
    private String remark;
}
