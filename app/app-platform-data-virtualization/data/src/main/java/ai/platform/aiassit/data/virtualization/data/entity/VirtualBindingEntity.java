package ai.platform.aiassit.data.virtualization.data.entity;

import ai.platform.aiassit.data.virtualization.api.config.BindingRoutingConfig;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.BindingRole;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "vd_binding", autoResultMap = true)
public class VirtualBindingEntity extends AuditableEntity {
    @TableField("entity_id") private Long entityId;
    @TableField("binding_code") private String bindingCode;
    @TableField("binding_group") private String bindingGroup;
    @TableField(value = "binding_role", typeHandler = DefaultEnumTypeHandler.class) private BindingRole bindingRole;
    @TableField("physical_table_meta_id") private Long physicalTableMetaId;
    @TableField("source_key") private String sourceKey;
    @TableField("physical_table_name") private String physicalTableName;
    @TableField("readable") private Boolean readable;
    @TableField("writable") private Boolean writable;
    @TableField("read_weight") private Integer readWeight;
    @TableField("write_priority") private Integer writePriority;
    @TableField(value = "routing_config", typeHandler = JacksonTypeHandler.class) private BindingRoutingConfig routingConfig;
    @TableField("enabled") private Boolean enabled;
    @TableField("remark") private String remark;
}
