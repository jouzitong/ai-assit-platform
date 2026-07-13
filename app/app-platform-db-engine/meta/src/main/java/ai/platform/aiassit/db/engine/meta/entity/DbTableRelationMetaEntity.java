package ai.platform.aiassit.db.engine.meta.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.AuditableEntity;

/**
 * 数据表关联元数据实体。
 *
 * <p>每条记录描述一组源字段到目标字段的映射；联合关系通过相同的关系名称分组。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("db_table_relation_meta")
public class DbTableRelationMetaEntity extends AuditableEntity {

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

    /** 关系名称。 */
    @JdbcColumn(
            name = "relation_name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "关系名称"
    )
    @TableField("relation_name")
    private String relationName;

    /** 源表名。 */
    @JdbcColumn(
            name = "source_table_name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "源表名"
    )
    @TableField("source_table_name")
    private String sourceTableName;

    /** 源字段名。 */
    @JdbcColumn(
            name = "source_column_name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "源字段名"
    )
    @TableField("source_column_name")
    private String sourceColumnName;

    /** 目标表名。 */
    @JdbcColumn(
            name = "target_table_name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "目标表名"
    )
    @TableField("target_table_name")
    private String targetTableName;

    /** 目标字段名。 */
    @JdbcColumn(
            name = "target_column_name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "目标字段名"
    )
    @TableField("target_column_name")
    private String targetColumnName;

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
