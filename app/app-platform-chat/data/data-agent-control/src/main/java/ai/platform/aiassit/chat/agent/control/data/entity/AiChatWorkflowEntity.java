package ai.platform.aiassit.chat.agent.control.data.entity;

import ai.platform.aiassit.chat.agent.control.data.entity.config.WorkflowCatalogConfig;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

/**
 * 流程目录表。
 *
 * <p>独立维护系统已实现的流程能力，不直接承载某次流程编排配置。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "agent_workflow", autoResultMap = true)
public class AiChatWorkflowEntity extends LogicalDeleteEntity {

    /**
     * 流程编码。
     */
    @JdbcColumn(
            name = "code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            unique = true,
            comment = "流程编码"
    )
    @TableField("code")
    private String code;

    /**
     * 流程名称。
     */
    @JdbcColumn(
            name = "name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "流程名称"
    )
    @TableField("name")
    private String name;

    /**
     * 流程类型，例如 QUERY/CHAT/APP。
     */
    @JdbcColumn(
            name = "type",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = false,
            comment = "流程类型"
    )
    @TableField("type")
    private String type;

    /**
     * 是否启用。
     */
    @JdbcColumn(
            name = "enabled",
            dataType = "TINYINT",
            nullable = false,
            defaultValue = "1",
            comment = "是否启用"
    )
    @TableField("enabled")
    private Boolean enabled = Boolean.TRUE;

    /**
     * 流程目录配置 JSON。
     */
    @JdbcColumn(
            name = "config",
            dataType = "MEDIUMTEXT",
            nullable = true,
            comment = "流程目录配置JSON"
    )
    @TableField(value = "config", typeHandler = JacksonTypeHandler.class)
    private WorkflowCatalogConfig config;
}
