package ai.platform.aiassit.db.engine.meta.entity;

import ai.platform.aiassit.db.engine.meta.entity.config.DataSourceConfig;
import ai.platform.aiassit.db.engine.meta.enums.DbDataSourceSyncMode;
import ai.platform.aiassit.db.engine.meta.enums.DbDataSourceType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

import java.time.LocalDateTime;

/**
 * 数据接入源实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "db_data_source", autoResultMap = true)
public class DbDataSourceEntity extends AuditableEntity {

    /** 数据源唯一标识。 */
    @JdbcColumn(
            name = "source_key",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            unique = true,
            comment = "数据源唯一标识"
    )
    @TableField("source_key")
    private String sourceKey;

    /** 数据源名称。 */
    @JdbcColumn(
            name = "source_name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "数据源名称"
    )
    @TableField("source_name")
    private String sourceName;

    /** 数据源类型，例如 DATABASE、HTTP_API、SERVICE_API、FILE、STREAM。 */
    @JdbcColumn(
            name = "source_type",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = false,
            comment = "数据源类型"
    )
    @TableField("source_type")
    private DbDataSourceType sourceType;

    /** 归属团队。 */
    @JdbcColumn(
            name = "owner_team",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = true,
            comment = "归属团队"
    )
    @TableField("owner_team")
    private String ownerTeam;

    /** 负责人。 */
    @JdbcColumn(
            name = "owner_user",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = true,
            comment = "负责人"
    )
    @TableField("owner_user")
    private String ownerUser;

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

    /** 同步方式，例如 REALTIME、T_PLUS_1、MANUAL。 */
    @JdbcColumn(
            name = "sync_mode",
            dataType = "TINYINT",
            nullable = false,
            defaultValue = "5",
            comment = "同步方式：0无需同步，1实时，2分钟级，3小时级，4T+1，5手动"
    )
    @TableField("sync_mode")
    private DbDataSourceSyncMode syncMode;

    /** 连接与认证等配置信息。 */
    @JdbcColumn(
            name = "config",
            dataType = "JSON",
            nullable = true,
            comment = "连接、认证及扩展配置"
    )
    @TableField(value = "config", typeHandler = JacksonTypeHandler.class)
    private DataSourceConfig config;

    /** 最近同步时间。 */
    @JdbcColumn(
            name = "last_sync_at",
            dataType = "DATETIME",
            nullable = true,
            comment = "最近同步时间"
    )
    @TableField("last_sync_at")
    private LocalDateTime lastSyncAt;

    /** 最近访问时间。 */
    @JdbcColumn(
            name = "last_access_at",
            dataType = "DATETIME",
            nullable = true,
            comment = "最近访问时间"
    )
    @TableField("last_access_at")
    private LocalDateTime lastAccessAt;

    /** 摘要说明。 */
    @JdbcColumn(
            name = "summary",
            dataType = "VARCHAR(512)",
            length = 512,
            nullable = true,
            comment = "摘要说明"
    )
    @TableField("summary")
    private String summary;

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
