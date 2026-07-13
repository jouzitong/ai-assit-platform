package ai.platform.aiassit.data.virtualization.data.entity;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "vd_entity", autoResultMap = true)
public class VirtualEntityEntity extends AuditableEntity {
    @JdbcColumn(
            name = "entity_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            unique = true,
            comment = "虚拟实体稳定编码"
    )
    @TableField("entity_code")
    private String entityCode;

    @JdbcColumn(
            name = "entity_name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "虚拟实体名称"
    )
    @TableField("entity_name")
    private String entityName;

    @JdbcColumn(
            name = "description",
            dataType = "VARCHAR(512)",
            length = 512,
            nullable = true,
            comment = "说明"
    )
    @TableField("description")
    private String description;

    @JdbcColumn(
            name = "status",
            dataType = "INT",
            nullable = false,
            defaultValue = "0",
            comment = "目录状态：0草稿，1已发布，2已停用"
    )
    @TableField(value = "status", typeHandler = DefaultEnumTypeHandler.class)
    private CatalogStatus status;

    @JdbcColumn(
            name = "catalog_version",
            dataType = "BIGINT",
            nullable = false,
            defaultValue = "0",
            comment = "目录发布版本"
    )
    @TableField("catalog_version")
    private Long catalogVersion;

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
