package ai.platform.aiassit.conversation.data.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.AuditableEntity;

import java.time.Instant;

/** AI 对话执行活动；同一协议 activityCode（correlationCode）的生命周期维护在同一条记录中。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("conversation_activity")
public class ConversationActivityEntity extends AuditableEntity {

    @JdbcColumn(name = "activity_code", dataType = "VARCHAR(64)", length = 64, nullable = false, unique = true, comment = "活动事件编码")
    @TableField("activity_code")
    private String activityCode;

    @JdbcColumn(name = "session_code", dataType = "VARCHAR(64)", length = 64, nullable = false, comment = "会话编码")
    @TableField("session_code")
    private String sessionCode;

    @JdbcColumn(name = "round_code", dataType = "VARCHAR(64)", length = 64, nullable = false, comment = "轮次编码")
    @TableField("round_code")
    private String roundCode;

    @JdbcColumn(name = "user_id", dataType = "BIGINT", nullable = false, defaultValue = "0", comment = "用户ID")
    @TableField("user_id")
    private Long userId;

    @JdbcColumn(name = "agent_code", dataType = "VARCHAR(64)", length = 64, nullable = true, comment = "执行 Agent 编码")
    @TableField("agent_code")
    private String agentCode;

    @JdbcColumn(name = "correlation_code", dataType = "VARCHAR(128)", length = 128, nullable = true, comment = "同一活动生命周期关联编码")
    @TableField("correlation_code")
    private String correlationCode;

    @JdbcColumn(name = "activity_type", dataType = "VARCHAR(32)", length = 32, nullable = false, comment = "活动类型")
    @TableField("activity_type")
    private String activityType;

    @JdbcColumn(name = "activity_name", dataType = "VARCHAR(128)", length = 128, nullable = false, comment = "活动名称")
    @TableField("activity_name")
    private String activityName;

    @JdbcColumn(name = "source", dataType = "VARCHAR(64)", length = 64, nullable = false, comment = "活动来源")
    @TableField("source")
    private String source;

    @JdbcColumn(name = "phase", dataType = "VARCHAR(32)", length = 32, nullable = true, comment = "活动阶段")
    @TableField("phase")
    private String phase;

    @JdbcColumn(name = "status", dataType = "VARCHAR(32)", length = 32, nullable = false, defaultValue = "'RUNNING'", comment = "活动状态")
    @TableField("status")
    private String status;

    @JdbcColumn(name = "message", dataType = "VARCHAR(512)", length = 512, nullable = true, comment = "活动展示信息")
    @TableField("message")
    private String message;

    @JdbcColumn(name = "input_summary", dataType = "MEDIUMTEXT", nullable = true, comment = "活动输入摘要")
    @TableField("input_summary")
    private String inputSummary;

    @JdbcColumn(name = "output_summary", dataType = "MEDIUMTEXT", nullable = true, comment = "活动输出摘要")
    @TableField("output_summary")
    private String outputSummary;

    @JdbcColumn(name = "duration_ms", dataType = "BIGINT", nullable = true, comment = "耗时毫秒")
    @TableField("duration_ms")
    private Long durationMs;

    @JdbcColumn(name = "started_at", dataType = "DATETIME", nullable = true, comment = "活动开始时间")
    @TableField("started_at")
    private Instant startedAt;

    @JdbcColumn(name = "finished_at", dataType = "DATETIME", nullable = true, comment = "活动结束时间")
    @TableField("finished_at")
    private Instant finishedAt;

    @JdbcColumn(name = "request_id", dataType = "VARCHAR(128)", length = 128, nullable = true, comment = "请求追踪编码")
    @TableField("request_id")
    private String requestId;

    @JdbcColumn(name = "seq_no", dataType = "INT", nullable = false, defaultValue = "1", comment = "轮次内事件顺序")
    @TableField("seq_no")
    private Integer seqNo;

    @JdbcColumn(name = "detail_json", dataType = "MEDIUMTEXT", nullable = true, comment = "活动结构化详情")
    @TableField("detail_json")
    private String detailJson;
}
