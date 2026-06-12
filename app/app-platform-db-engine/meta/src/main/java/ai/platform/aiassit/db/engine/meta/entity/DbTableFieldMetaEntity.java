package ai.platform.aiassit.db.engine.meta.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.AuditableEntity;

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
    @TableField("source_key")
    private String sourceKey;

    /** 所属表名。 */
    @TableField("table_name")
    private String tableName;

    /** 字段名。 */
    @TableField("column_name")
    private String columnName;

    /** 字段中文说明。 */
    @TableField("column_comment")
    private String columnComment;

    /** 字段类型。 */
    @TableField("data_type")
    private String dataType;

    /** 字段长度。 */
    @TableField("column_length")
    private Integer columnLength;

    /** 数值精度。 */
    @TableField("column_precision")
    private Integer columnPrecision;

    /** 数值小数位。 */
    @TableField("column_scale")
    private Integer columnScale;

    /** 是否可空。 */
    @TableField("nullable")
    private Boolean nullable;

    /** 是否主键。 */
    @TableField("primary_key")
    private Boolean primaryKey;

    /** 是否分区键。 */
    @TableField("partition_key")
    private Boolean partitionKey;

    /** 默认值。 */
    @TableField("default_value")
    private String defaultValue;

    /** 字段顺序。 */
    @TableField("ordinal_position")
    private Integer ordinalPosition;

    /** 字段角色，例如 DIMENSION、METRIC、TIME、ATTRIBUTE。 */
    @TableField("field_role")
    private String fieldRole;

    /** 是否启用。 */
    @TableField("enabled")
    private Boolean enabled;

    /** 备注。 */
    @TableField("remark")
    private String remark;
}
