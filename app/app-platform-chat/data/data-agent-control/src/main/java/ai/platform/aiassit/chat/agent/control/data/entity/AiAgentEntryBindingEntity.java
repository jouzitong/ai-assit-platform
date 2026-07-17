package ai.platform.aiassit.chat.agent.control.data.entity;

import ai.platform.aiassit.chat.agent.control.data.enums.AgentRuntimeType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

/** Maps a stable product entry code such as HOME_CHAT to a published Agent version and runtime. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "agent_entry_binding", autoResultMap = true)
public class AiAgentEntryBindingEntity extends LogicalDeleteEntity {

    @JdbcColumn(name = "entry_code", dataType = "VARCHAR(64)", length = 64, nullable = false,
            comment = "产品入口编码，例如 HOME_CHAT")
    @TableField("entry_code")
    private String entryCode;

    @JdbcColumn(name = "agent_code", dataType = "VARCHAR(64)", length = 64, nullable = false,
            comment = "绑定 Agent 编码")
    @TableField("agent_code")
    private String agentCode;

    @JdbcColumn(name = "agent_version", dataType = "INT", nullable = false,
            comment = "绑定的已发布 Agent 版本")
    @TableField("agent_version")
    private Integer agentVersion;

    @JdbcColumn(name = "runtime_type", dataType = "INT", nullable = false,
            comment = "运行时：1=OpenAI Agents Python,2=OpenAI Agents TypeScript")
    @TableField(value = "runtime_type", typeHandler = DefaultEnumTypeHandler.class)
    private AgentRuntimeType runtimeType;

    @JdbcColumn(name = "sdk_version", dataType = "VARCHAR(64)", length = 64, nullable = true,
            comment = "目标 Agent SDK 版本约束")
    @TableField("sdk_version")
    private String sdkVersion;

    @JdbcColumn(name = "priority", dataType = "INT", nullable = false, defaultValue = "100",
            comment = "同入口候选优先级，值越小优先")
    @TableField("priority")
    private Integer priority = 100;

    @JdbcColumn(name = "enabled", dataType = "BOOLEAN", nullable = false, defaultValue = "TRUE",
            comment = "是否启用")
    @TableField("enabled")
    private Boolean enabled = Boolean.TRUE;

    @JdbcColumn(name = "config_json", dataType = "MEDIUMTEXT", nullable = true,
            comment = "入口级非敏感运行配置 JSON")
    @TableField("config_json")
    private String configJson;
}
