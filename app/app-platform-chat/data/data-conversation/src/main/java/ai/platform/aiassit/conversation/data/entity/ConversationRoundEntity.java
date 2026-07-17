package ai.platform.aiassit.conversation.data.entity;

import ai.platform.aiassit.conversation.data.enums.ConversationRoundType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

/**
 * AI 对话轮次实体。
 *
 * <p>用于记录一次会话中的单轮对话执行信息。一个会话可以包含多个对话轮次，
 * 每个轮次通常对应一次用户提问及其后续的 AI 分析、工具调用、结果生成等过程。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("conversation_round")
public class ConversationRoundEntity extends LogicalDeleteEntity {

    /**
     * 对话轮次唯一编码。
     */
    @JdbcColumn(
            name = "round_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            unique = true,
            comment = "轮次编码"
    )
    @TableField("round_code")
    private String roundCode;

    /**
     * 轮次类型，用于区分普通问答、重试、追问、Agent 协作和工具执行等不同轮次场景。
     */
    @JdbcColumn(
            name = "round_type",
            dataType = "INT",
            nullable = false,
            defaultValue = "1",
            comment = "轮次类型"
    )
    @TableField("round_type")
    private ConversationRoundType roundType;

    /**
     * 父轮次编码，用于建立轮次之间的上下文或派生关系。
     */
    @JdbcColumn(
            name = "parent_round_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = true,
            comment = "父轮次编码"
    )
    @TableField("parent_round_code")
    private String parentRoundCode;

    /**
     * 会话编码，用于标识当前轮次所属的完整会话。
     */
    @JdbcColumn(
            name = "session_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            comment = "会话编码"
    )
    @TableField("session_code")
    private String sessionCode;

    /**
     * 用户 ID，用于标识当前轮次所属用户。
     */
    @JdbcColumn(
            name = "user_id",
            dataType = "BIGINT",
            nullable = false,
            defaultValue = "0",
            comment = "用户ID"
    )
    @TableField("user_id")
    private Long userId;

    /**
     * 模型配置编码，表示当前轮次期望使用的模型配置。
     */
    @JdbcColumn(
            name = "model_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = true,
            comment = "模型编码"
    )
    @TableField("model_code")
    private String modelCode;

    /**
     * 实际调用的模型名称，用于记录最终真实执行的模型。
     */
    @JdbcColumn(
            name = "actual_model",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = true,
            comment = "实际调用模型"
    )
    @TableField("actual_model")
    private String actualModel;

    @JdbcColumn(name = "agent_run_id", dataType = "VARCHAR(64)", length = 64, nullable = true,
            comment = "Agent运行ID")
    @TableField("agent_run_id")
    private String agentRunId;

    @JdbcColumn(name = "root_agent_code", dataType = "VARCHAR(64)", length = 64, nullable = true,
            comment = "根Agent编码")
    @TableField("root_agent_code")
    private String rootAgentCode;

    @JdbcColumn(name = "root_agent_version", dataType = "INT", nullable = true,
            comment = "根Agent版本")
    @TableField("root_agent_version")
    private Integer rootAgentVersion;

    @JdbcColumn(name = "agent_runtime_type", dataType = "VARCHAR(32)", length = 32, nullable = true,
            comment = "Agent运行时类型")
    @TableField("agent_runtime_type")
    private String agentRuntimeType;

    @JdbcColumn(name = "agent_sdk_version", dataType = "VARCHAR(64)", length = 64, nullable = true,
            comment = "Agent SDK版本")
    @TableField("agent_sdk_version")
    private String agentSdkVersion;

    @JdbcColumn(name = "agent_snapshot_hash", dataType = "VARCHAR(80)", length = 80, nullable = true,
            comment = "Agent快照哈希")
    @TableField("agent_snapshot_hash")
    private String agentSnapshotHash;

    /**
     * 轮次状态，例如处理中、成功、失败、取消。
     */
    @JdbcColumn(
            name = "status",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = false,
            defaultValue = "'SUCCESS'",
            comment = "状态"
    )
    @TableField("status")
    private String status;
}
