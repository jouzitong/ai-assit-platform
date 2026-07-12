package ai.platform.aiassit.db.engine.meta.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

/**
 * 数据表字段元数据实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("db_table_field_meta")
public class DbTableFieldMetaEntity extends AuditableEntity {

    /** 所属数据源标识。 */
    @JdbcColumn(
            name = "source_key",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            comment = "所属数据源标识"
    )
    @TableField("source_key")
    private String sourceKey;

    /** 所属表名。 */
    @JdbcColumn(
            name = "table_name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "所属表名"
    )
    @TableField("table_name")
    private String tableName;

    /** 字段名。 */
    @JdbcColumn(
            name = "column_name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "字段名"
    )
    @TableField("column_name")
    private String columnName;

    /** 字段中文说明。 */
    @JdbcColumn(
            name = "column_comment",
            dataType = "VARCHAR(512)",
            length = 512,
            nullable = true,
            comment = "字段中文说明"
    )
    @TableField("column_comment")
    private String columnComment;

    /** 字段类型。 */
    @JdbcColumn(
            name = "data_type",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            comment = "字段类型"
    )
    @TableField("data_type")
    private String dataType;

    /** 字段长度。 */
    @JdbcColumn(
            name = "column_length",
            dataType = "INT",
            nullable = true,
            comment = "字段长度"
    )
    @TableField("column_length")
    private Integer columnLength;

    /** 数值精度。 */
    @JdbcColumn(
            name = "column_precision",
            dataType = "INT",
            nullable = true,
            comment = "数值精度"
    )
    @TableField("column_precision")
    private Integer columnPrecision;

    /** 数值小数位。 */
    @JdbcColumn(
            name = "column_scale",
            dataType = "INT",
            nullable = true,
            comment = "数值小数位"
    )
    @TableField("column_scale")
    private Integer columnScale;

    /** 是否可空。 */
    @JdbcColumn(
            name = "nullable",
            dataType = "TINYINT",
            nullable = false,
            defaultValue = "TRUE",
            comment = "是否可空"
    )
    @TableField("nullable")
    private Boolean nullable;

    /** 是否主键。 */
    @JdbcColumn(
            name = "primary_key",
            dataType = "TINYINT",
            nullable = true,
            defaultValue = "FALSE",
            comment = "是否主键"
    )
    @TableField("primary_key")
    private Boolean primaryKey;

    /** 是否分区键。 */
    @JdbcColumn(
            name = "partition_key",
            dataType = "TINYINT",
            nullable = true,
            defaultValue = "FALSE",
            comment = "是否分区键"
    )
    @TableField("partition_key")
    private Boolean partitionKey;

    /** 默认值。 */
    @JdbcColumn(
            name = "default_value",
            dataType = "VARCHAR(512)",
            length = 512,
            nullable = true,
            comment = "默认值"
    )
    @TableField("default_value")
    private String defaultValue;

    /** 字段顺序。 */
    @JdbcColumn(
            name = "ordinal_position",
            dataType = "INT",
            nullable = true,
            comment = "字段顺序"
    )
    @TableField("ordinal_position")
    private Integer ordinalPosition;

    /** 字段角色，例如 DIMENSION、METRIC、TIME、ATTRIBUTE。 */
    @JdbcColumn(
            name = "field_role",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = true,
            comment = "字段角色"
    )
    @TableField("field_role")
    private String fieldRole;

    /** 是否启用。 */
    @JdbcColumn(
            name = "enabled",
            dataType = "BOOLEAN",
            nullable = false,
            defaultValue = "TRUE",
            comment = "是否启用"
    )
    @TableField("enabled")
    private Boolean enabled;

    /** 备注。 */
    @JdbcColumn(
            name = "remark",
            dataType = "VARCHAR(512)",
            length = 512,
            nullable = true,
            comment = "备注"
    )
    @TableField("remark")
    private String remark;
}
