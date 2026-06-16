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

    @TableField("job_id")
    private String jobId;

    @TableField("source_key")
    private String sourceKey;

    @TableField("file_name")
    private String fileName;

    @TableField("content_type")
    private String contentType;

    @TableField("status")
    private DbMetaImportJobStatus status;

    @TableField("stage")
    private DbMetaImportJobStage stage;

    @TableField("progress_percent")
    private Integer progressPercent;

    @TableField("message")
    private String message;

    @TableField("recent_messages_json")
    private String recentMessagesJson;

    @TableField("table_total")
    private Integer tableTotal;

    @TableField("table_processed")
    private Integer tableProcessed;

    @TableField("table_created_count")
    private Integer tableCreatedCount;

    @TableField("table_updated_count")
    private Integer tableUpdatedCount;

    @TableField("field_total")
    private Integer fieldTotal;

    @TableField("field_processed")
    private Integer fieldProcessed;

    @TableField("field_created_count")
    private Integer fieldCreatedCount;

    @TableField("field_updated_count")
    private Integer fieldUpdatedCount;

    @TableField("index_total")
    private Integer indexTotal;

    @TableField("index_processed")
    private Integer indexProcessed;

    @TableField("index_created_count")
    private Integer indexCreatedCount;

    @TableField("index_updated_count")
    private Integer indexUpdatedCount;

    @TableField("result_json")
    private String resultJson;
}
