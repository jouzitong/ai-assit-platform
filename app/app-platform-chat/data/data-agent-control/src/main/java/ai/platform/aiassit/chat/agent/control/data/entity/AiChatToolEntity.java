package ai.platform.aiassit.chat.agent.control.data.entity;

import ai.platform.aiassit.chat.agent.control.data.enums.AiChatToolSyncStatus;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

/**
 * Tool 目录表。
 *
 * <p>维护对 Agent 暴露的工具定义，脚本内容直接存储在表中。</p>
 *
 * @author zhouzhitong
 * @since 2026/7/7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "agent_tool", autoResultMap = true)
public class AiChatToolEntity extends LogicalDeleteEntity {

    /**
     * Tool 编码。
     */
    @JdbcColumn(
            name = "code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            unique = true,
            comment = "Tool 编码。"
    )
    @TableField("code")
    private String code;

    /**
     * Tool 名称 / Agent 暴露名。
     */
    @JdbcColumn(
            name = "name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "Tool 名称 / Agent 暴露名。"
    )
    @TableField("name")
    private String name;

    /**
     * Tool 说明。
     */
    @JdbcColumn(
            name = "desc",
            dataType = "VARCHAR(1024)",
            length = 1024,
            nullable = true,
            comment = "Tool 说明。"
    )
    @TableField("`desc`")
    private String desc;

    /**
     * 脚本内容。
     */
    @JdbcColumn(
            name = "content",
            dataType = "MEDIUMTEXT",
            nullable = true,
            comment = "工具脚本内容。"
    )
    @TableField("content")
    private String content;

    /**
     * 运行时类型，例如 PYTHON / JAVASCRIPT。
     */
    @JdbcColumn(
            name = "runtime_type",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = true,
            comment = "运行时类型，例如 PYTHON / JAVASCRIPT。"
    )
    @TableField("runtime_type")
    private String runtimeType;

    /**
     * 同步状态，例如 PENDING / SUCCESS / FAILED。
     */
    @JdbcColumn(
            name = "sync_status",
            dataType = "INT",
            nullable = false,
            defaultValue = "1",
            comment = "同步状态，例如 PENDING / SUCCESS / FAILED。"
    )
    @TableField(value = "sync_status", typeHandler = DefaultEnumTypeHandler.class)
    private AiChatToolSyncStatus syncStatus;

    /**
     * 是否启用。
     */
    @JdbcColumn(
            name = "enabled",
            dataType = "BOOLEAN",
            nullable = false,
            defaultValue = "TRUE",
            comment = "是否启用。"
    )
    @TableField("enabled")
    private Boolean enabled = Boolean.TRUE;

    /**
     * 备注。
     */
    @JdbcColumn(
            name = "remark",
            dataType = "VARCHAR(1024)",
            length = 1024,
            nullable = true,
            comment = "备注。"
    )
    @TableField("remark")
    private String remark;
}
