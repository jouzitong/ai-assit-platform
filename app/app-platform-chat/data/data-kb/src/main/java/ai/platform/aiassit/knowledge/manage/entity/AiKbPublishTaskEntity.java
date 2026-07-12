package ai.platform.aiassit.knowledge.manage.entity;

import ai.platform.aiassit.service.ai.api.enums.AiKbPublishStage;
import ai.platform.aiassit.service.ai.api.enums.AiKbTaskStatus;
import ai.platform.aiassit.service.ai.api.enums.AiKbTaskType;
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
import java.util.Map;

/**
 * 知识库发布任务实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_kb_publish_task", autoResultMap = true)
public class AiKbPublishTaskEntity extends AuditableEntity {

    /** 任务编码。 */
    @JdbcColumn(
            name = "task_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            unique = true,
            comment = "任务编码"
    )
    @TableField("task_code")
    private String taskCode;

    /** 所属知识库编码。 */
    @JdbcColumn(
            name = "kb_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            comment = "所属知识库编码"
    )
    @TableField("kb_code")
    private String kbCode;

    /** 任务类型，例如 PUBLISH、ROLLBACK。 */
    @JdbcColumn(
            name = "task_type",
            dataType = "INT",
            nullable = false,
            comment = "任务类型枚举编码：1=PUBLISH,2=ROLLBACK"
    )
    @TableField("task_type")
    private AiKbTaskType taskType;

    /** 任务状态，例如 PENDING、RUNNING、SUCCESS、FAILED、CANCELED。 */
    @JdbcColumn(
            name = "status",
            dataType = "INT",
            nullable = false,
            defaultValue = "1",
            comment = "任务状态枚举编码：1=PENDING,2=RUNNING,3=SUCCESS,4=FAILED,5=CANCELED"
    )
    @TableField("status")
    private AiKbTaskStatus status;

    /** 当前进度百分比。 */
    @JdbcColumn(
            name = "progress_percent",
            dataType = "INT",
            nullable = false,
            defaultValue = "0",
            comment = "当前进度百分比"
    )
    @TableField("progress_percent")
    private Integer progressPercent;

    /** 当前执行阶段。 */
    @JdbcColumn(
            name = "current_stage",
            dataType = "INT",
            nullable = true,
            comment = "当前执行阶段，例如 PREPARE_VERSION"
    )
    @TableField("current_stage")
    private AiKbPublishStage currentStage;

    /** 任务请求参数。 */
    @JdbcColumn(
            name = "request_json",
            dataType = "MEDIUMTEXT",
            nullable = true,
            comment = "任务请求参数 JSON"
    )
    @TableField(value = "request_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> requestJson;

    /** 任务执行结果。 */
    @JdbcColumn(
            name = "result_json",
            dataType = "MEDIUMTEXT",
            nullable = true,
            comment = "任务执行结果 JSON"
    )
    @TableField(value = "result_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> resultJson;

    /** 失败错误信息。 */
    @JdbcColumn(
            name = "error_message",
            dataType = "VARCHAR(2048)",
            length = 2048,
            nullable = true,
            comment = "失败错误信息"
    )
    @TableField("error_message")
    private String errorMessage;

    /** 启动时间。 */
    @JdbcColumn(
            name = "started_at",
            dataType = "DATETIME",
            nullable = true,
            comment = "启动时间"
    )
    @TableField("started_at")
    private LocalDateTime startedAt;

    /** 结束时间。 */
    @JdbcColumn(
            name = "finished_at",
            dataType = "DATETIME",
            nullable = true,
            comment = "结束时间"
    )
    @TableField("finished_at")
    private LocalDateTime finishedAt;
}
