package ai.platform.aiassit.chat.workflow.data.entity;

import ai.platform.aiassit.chat.workflow.data.entity.config.WorkflowRuntimeConfig;
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
 * 流程配置主表。
 *
 * <p>描述某个流程的一份可编辑配置，节点编排关系和流程级配置都挂在这里。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_chat_workflow_config", autoResultMap = true)
public class AiChatWorkflowConfigEntity extends LogicalDeleteEntity {

    /**
     * 配置编码。
     */
    @JdbcColumn(
            name = "code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            unique = true,
            comment = "配置编码"
    )
    @TableField("code")
    private String code;

    /**
     * 所属流程编码。
     */
    @JdbcColumn(
            name = "workflow_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            comment = "流程编码"
    )
    @TableField("workflow_code")
    private String workflowCode;

    /**
     * 配置名称。
     */
    @JdbcColumn(
            name = "name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "配置名称"
    )
    @TableField("name")
    private String name;

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
     * 流程级配置 JSON。
     */
    @JdbcColumn(
            name = "config",
            dataType = "MEDIUMTEXT",
            nullable = true,
            comment = "流程运行配置JSON"
    )
    @TableField(value = "config", typeHandler = JacksonTypeHandler.class)
    private WorkflowRuntimeConfig config;
}
