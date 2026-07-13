package ai.platform.aiassit.data.virtualization.data.entity;

import ai.platform.aiassit.data.virtualization.api.config.BindingRoutingConfig;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.BindingRole;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "vd_binding", autoResultMap = true)
public class VirtualBindingEntity extends AuditableEntity {
    @JdbcColumn(name = "entity_id", dataType = "BIGINT", nullable = false, comment = "虚拟实体ID")
    @TableField("entity_id")
    private Long entityId;

    @JdbcColumn(name = "binding_code", dataType = "VARCHAR(64)", length = 64, nullable = false, comment = "绑定稳定编码")
    @TableField("binding_code")
    private String bindingCode;

    @JdbcColumn(name = "binding_group", dataType = "VARCHAR(64)", length = 64, nullable = false, comment = "分片与副本组编码")
    @TableField("binding_group")
    private String bindingGroup;

    @JdbcColumn(name = "binding_role", dataType = "INT", nullable = false, defaultValue = "0", comment = "绑定角色：0主绑定，1副本")
    @TableField(value = "binding_role", typeHandler = DefaultEnumTypeHandler.class)
    private BindingRole bindingRole;

    @JdbcColumn(name = "physical_table_meta_id", dataType = "BIGINT", nullable = false, comment = "物理表元数据ID")
    @TableField("physical_table_meta_id")
    private Long physicalTableMetaId;

    @JdbcColumn(name = "source_key", dataType = "VARCHAR(64)", length = 64, nullable = false, comment = "数据源标识")
    @TableField("source_key")
    private String sourceKey;

    @JdbcColumn(name = "physical_table_name", dataType = "VARCHAR(128)", length = 128, nullable = false, comment = "真实表名快照")
    @TableField("physical_table_name")
    private String physicalTableName;

    @JdbcColumn(name = "readable", dataType = "BOOLEAN", nullable = false, defaultValue = "TRUE", comment = "是否可读")
    @TableField("readable")
    private Boolean readable;

    @JdbcColumn(name = "writable", dataType = "BOOLEAN", nullable = false, defaultValue = "FALSE", comment = "是否可写")
    @TableField("writable")
    private Boolean writable;

    @JdbcColumn(name = "read_weight", dataType = "INT", nullable = false, defaultValue = "100", comment = "读取权重")
    @TableField("read_weight")
    private Integer readWeight;

    @JdbcColumn(name = "write_priority", dataType = "INT", nullable = false, defaultValue = "0", comment = "写优先级")
    @TableField("write_priority")
    private Integer writePriority;

    @JdbcColumn(name = "routing_config", dataType = "JSON", nullable = true, comment = "强类型路由配置")
    @TableField(value = "routing_config", typeHandler = JacksonTypeHandler.class)
    private BindingRoutingConfig routingConfig;

    @JdbcColumn(name = "enabled", dataType = "BOOLEAN", nullable = false, defaultValue = "TRUE", comment = "是否启用")
    @TableField("enabled")
    private Boolean enabled;

    @JdbcColumn(name = "remark", dataType = "VARCHAR(512)", length = 512, nullable = true, comment = "备注")
    @TableField("remark")
    private String remark;
}
