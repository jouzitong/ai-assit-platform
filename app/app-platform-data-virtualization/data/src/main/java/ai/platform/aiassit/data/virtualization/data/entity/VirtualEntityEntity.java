package ai.platform.aiassit.data.virtualization.data.entity;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "vd_entity", autoResultMap = true)
public class VirtualEntityEntity extends AuditableEntity {
    @TableField("entity_code") private String entityCode;
    @TableField("entity_name") private String entityName;
    @TableField("description") private String description;
    @TableField(value = "status", typeHandler = DefaultEnumTypeHandler.class) private CatalogStatus status;
    @TableField("catalog_version") private Long catalogVersion;
    @TableField("enabled") private Boolean enabled;
}
