package ai.platform.aiassist.service.ai.kb.entity;

import ai.platform.aiassist.service.ai.api.enums.AiKbPublishStage;
import ai.platform.aiassist.service.ai.api.enums.AiKbTaskStatus;
import ai.platform.aiassist.service.ai.api.enums.AiKbTaskType;
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
    @TableField("task_code")
    private String taskCode;

    /** 所属知识库编码。 */
    @TableField("kb_code")
    private String kbCode;

    /** 所属知识库版本 ID。 */
    @TableField("kb_version_id")
    private Long kbVersionId;

    /** 任务类型，例如 PUBLISH、ROLLBACK。 */
    @TableField("task_type")
    private AiKbTaskType taskType;

    /** 任务状态，例如 PENDING、RUNNING、SUCCESS、FAILED、CANCELED。 */
    @TableField("status")
    private AiKbTaskStatus status;

    /** 当前进度百分比。 */
    @TableField("progress_percent")
    private Integer progressPercent;

    /** 当前执行阶段。 */
    @TableField("current_stage")
    private AiKbPublishStage currentStage;

    /** 任务请求参数。 */
    @TableField(value = "request_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> requestJson;

    /** 任务执行结果。 */
    @TableField(value = "result_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> resultJson;

    /** 失败错误信息。 */
    @TableField("error_message")
    private String errorMessage;

    /** 启动时间。 */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /** 结束时间。 */
    @TableField("finished_at")
    private LocalDateTime finishedAt;
}
