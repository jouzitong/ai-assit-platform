package ai.platform.aiassit.db.engine.meta.entity;

import ai.platform.aiassit.db.engine.meta.entity.config.DbDataSourceConfig;
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
    @TableField("source_key")
    private String sourceKey;

    /** 数据源名称。 */
    @TableField("source_name")
    private String sourceName;

    /** 数据源类型，例如 DATABASE、HTTP_API、SERVICE_API、FILE、STREAM。 */
    @TableField("source_type")
    private DbDataSourceType sourceType;

    /** 归属团队。 */
    @TableField("owner_team")
    private String ownerTeam;

    /** 负责人。 */
    @TableField("owner_user")
    private String ownerUser;

    /** 状态。 */
    @TableField("status")
    private String status;

    /** 是否启用。 */
    @TableField("enabled")
    private Boolean enabled;

    /** 同步方式，例如 REALTIME、T_PLUS_1、MANUAL。 */
    @TableField("sync_mode")
    private DbDataSourceSyncMode syncMode;

    /** 连接与认证等配置信息。 */
    @TableField(value = "config", typeHandler = JacksonTypeHandler.class)
    private DbDataSourceConfig config;

    /** 最近同步时间。 */
    @TableField("last_sync_at")
    private LocalDateTime lastSyncAt;

    /** 最近访问时间。 */
    @TableField("last_access_at")
    private LocalDateTime lastAccessAt;

    /** 摘要说明。 */
    @TableField("summary")
    private String summary;

    /** 备注。 */
    @TableField("remark")
    private String remark;
}
