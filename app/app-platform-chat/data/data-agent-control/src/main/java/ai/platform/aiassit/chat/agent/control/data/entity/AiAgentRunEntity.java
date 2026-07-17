package ai.platform.aiassit.chat.agent.control.data.entity;

import ai.platform.aiassit.chat.agent.control.data.enums.AgentRuntimeType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

import java.time.Instant;

/** Durable lifecycle audit for one Agent run. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "agent_run_audit", autoResultMap = true)
public class AiAgentRunEntity extends AuditableEntity {

    @JdbcColumn(name = "run_id", dataType = "VARCHAR(64)", length = 64, nullable = false, unique = true,
            comment = "Agent run id")
    @TableField("run_id")
    private String runId;

    @JdbcColumn(name = "session_code", dataType = "VARCHAR(64)", length = 64, nullable = true,
            comment = "会话编码")
    @TableField("session_code")
    private String sessionCode;

    @JdbcColumn(name = "round_code", dataType = "VARCHAR(64)", length = 64, nullable = true,
            comment = "轮次编码")
    @TableField("round_code")
    private String roundCode;

    @JdbcColumn(name = "root_agent_code", dataType = "VARCHAR(64)", length = 64, nullable = false,
            comment = "根 Agent 编码")
    @TableField("root_agent_code")
    private String rootAgentCode;

    @JdbcColumn(name = "root_agent_version", dataType = "INT", nullable = false,
            comment = "根 Agent 版本")
    @TableField("root_agent_version")
    private Integer rootAgentVersion;

    @JdbcColumn(name = "workflow_code", dataType = "VARCHAR(64)", length = 64, nullable = true,
            comment = "Workflow 编码")
    @TableField("workflow_code")
    private String workflowCode;

    @JdbcColumn(name = "workflow_version", dataType = "INT", nullable = true,
            comment = "Workflow 版本")
    @TableField("workflow_version")
    private Integer workflowVersion;

    @JdbcColumn(name = "runtime_type", dataType = "INT", nullable = false,
            comment = "运行时类型")
    @TableField(value = "runtime_type", typeHandler = DefaultEnumTypeHandler.class)
    private AgentRuntimeType runtimeType;

    @JdbcColumn(name = "sdk_version", dataType = "VARCHAR(64)", length = 64, nullable = true,
            comment = "Agent SDK 版本")
    @TableField("sdk_version")
    private String sdkVersion;

    @JdbcColumn(name = "snapshot_hash", dataType = "VARCHAR(80)", length = 80, nullable = false,
            comment = "冻结定义摘要（含算法前缀）")
    @TableField("snapshot_hash")
    private String snapshotHash;

    @JdbcColumn(name = "trace_id", dataType = "VARCHAR(128)", length = 128, nullable = true,
            comment = "Trace id")
    @TableField("trace_id")
    private String traceId;

    @JdbcColumn(name = "status", dataType = "VARCHAR(32)", length = 32, nullable = false,
            comment = "运行状态")
    @TableField("status")
    private String status;

    @JdbcColumn(name = "started_at", dataType = "DATETIME", nullable = true, comment = "开始时间")
    @TableField("started_at")
    private Instant startedAt;

    @JdbcColumn(name = "finished_at", dataType = "DATETIME", nullable = true, comment = "结束时间")
    @TableField("finished_at")
    private Instant finishedAt;

    @JdbcColumn(name = "usage_json", dataType = "MEDIUMTEXT", nullable = true, comment = "用量 JSON")
    @TableField("usage_json")
    private String usageJson;

    @JdbcColumn(name = "error_summary", dataType = "VARCHAR(1024)", length = 1024, nullable = true,
            comment = "脱敏错误摘要")
    @TableField("error_summary")
    private String errorSummary;
}
