package ai.platform.aiassit.db.engine.meta.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

/**
 * 数据表索引元数据实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("db_table_index_meta")
public class DbTableIndexMetaEntity extends LogicalDeleteEntity {

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

    /** 索引名称。 */
    @JdbcColumn(
            name = "index_name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "索引名称"
    )
    @TableField("index_name")
    private String indexName;

    /** 索引类型，例如 PRIMARY、UNIQUE、NORMAL。 */
    @JdbcColumn(
            name = "index_type",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = false,
            comment = "索引类型"
    )
    @TableField("index_type")
    private String indexType;

    /** 是否唯一索引。 */
    @JdbcColumn(
            name = "unique_flag",
            dataType = "BOOLEAN",
            nullable = false,
            defaultValue = "FALSE",
            comment = "是否唯一索引"
    )
    @TableField("unique_flag")
    private Boolean uniqueFlag;

    /** 是否主键索引。 */
    @JdbcColumn(
            name = "primary_flag",
            dataType = "BOOLEAN",
            nullable = false,
            defaultValue = "FALSE",
            comment = "是否主键索引"
    )
    @TableField("primary_flag")
    private Boolean primaryFlag;

    /** 索引字段名。 */
    @JdbcColumn(
            name = "column_name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "索引字段名"
    )
    @TableField("column_name")
    private String columnName;

    /** 字段在索引内的顺序。 */
    @JdbcColumn(
            name = "column_order",
            dataType = "INT",
            nullable = false,
            defaultValue = "1",
            comment = "字段在索引内的顺序"
    )
    @TableField("column_order")
    private Integer columnOrder;

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
