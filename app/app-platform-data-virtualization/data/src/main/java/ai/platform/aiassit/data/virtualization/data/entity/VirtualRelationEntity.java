package ai.platform.aiassit.data.virtualization.data.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.AuditableEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vd_relation")
public class VirtualRelationEntity extends AuditableEntity {
    @TableField("relation_code") private String relationCode;
    @TableField("relation_name") private String relationName;
    @TableField("source_entity_id") private Long sourceEntityId;
    @TableField("source_field_id") private Long sourceFieldId;
    @TableField("target_entity_id") private Long targetEntityId;
    @TableField("target_field_id") private Long targetFieldId;
    @TableField("enabled") private Boolean enabled;
    @TableField("remark") private String remark;
}
