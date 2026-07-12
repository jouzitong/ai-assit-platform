package ai.platform.aiassit.db.engine.meta.entity;

import ai.platform.aiassit.db.engine.meta.enums.DbMetaImportJobStage;
import ai.platform.aiassit.db.engine.meta.enums.DbMetaImportJobStatus;
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
 * 导入任务状态持久化实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("db_meta_import_job")
public class DbMetaImportJobEntity extends AuditableEntity {

    @JdbcColumn(name = "job_id", comment = "任务标识")
    @TableField("job_id")
    private String jobId;

    @JdbcColumn(name = "source_key", comment = "数据源标识")
    @TableField("source_key")
    private String sourceKey;

    @JdbcColumn(name = "file_name", comment = "文件名称")
    @TableField("file_name")
    private String fileName;

    @JdbcColumn(name = "content_type", comment = "内容类型")
    @TableField("content_type")
    private String contentType;

    @JdbcColumn(name = "status", comment = "状态")
    @TableField("status")
    private DbMetaImportJobStatus status;

    @JdbcColumn(name = "stage", comment = "执行阶段")
    @TableField("stage")
    private DbMetaImportJobStage stage;

    @JdbcColumn(name = "progress_percent", comment = "进度百分比")
    @TableField("progress_percent")
    private Integer progressPercent;

    @JdbcColumn(name = "message", comment = "消息")
    @TableField("message")
    private String message;

    @JdbcColumn(name = "recent_messages_json", comment = "最近消息 JSON")
    @TableField("recent_messages_json")
    private String recentMessagesJson;

    @JdbcColumn(name = "table_total", comment = "表总数")
    @TableField("table_total")
    private Integer tableTotal;

    @JdbcColumn(name = "table_processed", comment = "已处理表数")
    @TableField("table_processed")
    private Integer tableProcessed;

    @JdbcColumn(name = "table_created_count", comment = "新建表数")
    @TableField("table_created_count")
    private Integer tableCreatedCount;

    @JdbcColumn(name = "table_updated_count", comment = "更新表数")
    @TableField("table_updated_count")
    private Integer tableUpdatedCount;

    @JdbcColumn(name = "field_total", comment = "字段总数")
    @TableField("field_total")
    private Integer fieldTotal;

    @JdbcColumn(name = "field_processed", comment = "已处理字段数")
    @TableField("field_processed")
    private Integer fieldProcessed;

    @JdbcColumn(name = "field_created_count", comment = "新建字段数")
    @TableField("field_created_count")
    private Integer fieldCreatedCount;

    @JdbcColumn(name = "field_updated_count", comment = "更新字段数")
    @TableField("field_updated_count")
    private Integer fieldUpdatedCount;

    @JdbcColumn(name = "index_total", comment = "索引总数")
    @TableField("index_total")
    private Integer indexTotal;

    @JdbcColumn(name = "index_processed", comment = "已处理索引数")
    @TableField("index_processed")
    private Integer indexProcessed;

    @JdbcColumn(name = "index_created_count", comment = "新建索引数")
    @TableField("index_created_count")
    private Integer indexCreatedCount;

    @JdbcColumn(name = "index_updated_count", comment = "更新索引数")
    @TableField("index_updated_count")
    private Integer indexUpdatedCount;

    @JdbcColumn(name = "result_json", comment = "结果 JSON")
    @TableField("result_json")
    private String resultJson;
}
