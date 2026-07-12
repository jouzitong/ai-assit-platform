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

import java.time.LocalDateTime;

/**
 * 数据表元数据实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("db_table_meta")
public class DbTableMetaEntity extends AuditableEntity {

    /** 所属数据源标识。 */
    @JdbcColumn(name = "source_key", comment = "所属数据源标识。")
    @TableField("source_key")
    private String sourceKey;

    /** 表名。 */
    @JdbcColumn(name = "table_name", comment = "表名。")
    @TableField("table_name")
    private String tableName;

    /** 表中文说明。 */
    @JdbcColumn(name = "table_comment", comment = "表中文说明。")
    @TableField("table_comment")
    private String tableComment;

    /** 表类型，例如 TABLE、VIEW、API_OBJECT。 */
    @JdbcColumn(name = "table_type", comment = "表类型，例如 TABLE、VIEW、API_OBJECT。")
    @TableField("table_type")
    private String tableType;

    /** 分层类型，例如 ODS、DWD、DWS、ADS。 */
    @JdbcColumn(name = "layer_type", comment = "分层类型，例如 ODS、DWD、DWS、ADS。")
    @TableField("layer_type")
    private String layerType;

    /** 数据量快照。 */
    @JdbcColumn(name = "row_count", comment = "数据量快照。")
    @TableField("row_count")
    private Long rowCount;

    /** 字段数快照。 */
    @JdbcColumn(name = "column_count", comment = "字段数快照。")
    @TableField("column_count")
    private Integer columnCount;

    /** 分区键。 */
    @JdbcColumn(name = "partition_key", comment = "分区键。")
    @TableField("partition_key")
    private String partitionKey;

    /** 数据新鲜度，单位秒。 */
    @JdbcColumn(name = "freshness_seconds", comment = "数据新鲜度，单位秒。")
    @TableField("freshness_seconds")
    private Integer freshnessSeconds;

    /** 状态。 */
    @JdbcColumn(name = "status", comment = "状态。")
    @TableField("status")
    private String status;

    /** 是否启用。 */
    @JdbcColumn(name = "enabled", comment = "是否启用。")
    @TableField("enabled")
    private Boolean enabled;

    /** 最近扫描时间。 */
    @JdbcColumn(name = "last_scan_at", comment = "最近扫描时间。")
    @TableField("last_scan_at")
    private LocalDateTime lastScanAt;

    /** 最近同步时间。 */
    @JdbcColumn(name = "last_sync_at", comment = "最近同步时间。")
    @TableField("last_sync_at")
    private LocalDateTime lastSyncAt;

    /** 备注。 */
    @JdbcColumn(name = "remark", comment = "备注。")
    @TableField("remark")
    private String remark;
}
