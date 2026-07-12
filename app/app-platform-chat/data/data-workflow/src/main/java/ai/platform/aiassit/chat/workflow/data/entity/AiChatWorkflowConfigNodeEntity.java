package ai.platform.aiassit.chat.workflow.data.entity;

import ai.platform.aiassit.chat.workflow.data.entity.config.WorkflowNodeRuntimeConfig;
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
 * 流程配置节点表。
 *
 * <p>把节点目录挂到某份流程配置上，并保存节点级配置。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_chat_workflow_config_node", autoResultMap = true)
public class AiChatWorkflowConfigNodeEntity extends LogicalDeleteEntity {

    /**
     * 所属流程配置编码。
     */
    @JdbcColumn(
            name = "config_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            comment = "流程配置编码"
    )
    @TableField("config_code")
    private String configCode;

    /**
     * 节点编码。
     */
    @JdbcColumn(
            name = "node_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            comment = "节点编码"
    )
    @TableField("node_code")
    private String nodeCode;

    /**
     * 节点顺序。
     */
    @JdbcColumn(
            name = "sort",
            dataType = "INT",
            nullable = false,
            defaultValue = "1",
            comment = "节点顺序"
    )
    @TableField("sort")
    private Integer sort;

    /**
     * 默认下一节点编码。
     */
    @JdbcColumn(
            name = "next_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = true,
            comment = "下一节点编码"
    )
    @TableField("next_code")
    private String nextCode;

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
     * 节点配置 JSON。
     * 可放输入输出定义、节点提示模板、运行参数、回跳策略等。
     */
    @JdbcColumn(
            name = "config",
            dataType = "MEDIUMTEXT",
            nullable = true,
            comment = "节点运行配置JSON"
    )
    @TableField(value = "config", typeHandler = JacksonTypeHandler.class)
    private WorkflowNodeRuntimeConfig config;
}
