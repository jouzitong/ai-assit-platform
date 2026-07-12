package ai.platform.aiassit.chat.workflow.data.entity;

import ai.platform.aiassit.chat.workflow.data.enums.AiChatToolSyncStatus;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

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
@TableName("ai_chat_tool")
public class AiChatToolEntity extends LogicalDeleteEntity {

    /**
     * Tool 编码。
     */
    @JdbcColumn(
            name = "code",
            dataType = "VARCHAR(255)",
            length = 255,
            nullable = true,
            comment = "Tool 编码。"
    )
    @TableField("code")
    private String code;

    /**
     * Tool 名称 / Agent 暴露名。
     */
    @JdbcColumn(
            name = "name",
            dataType = "VARCHAR(255)",
            length = 255,
            nullable = true,
            comment = "Tool 名称 / Agent 暴露名。"
    )
    @TableField("name")
    private String name;

    /**
     * Tool 说明。
     */
    @JdbcColumn(
            name = "desc",
            dataType = "VARCHAR(255)",
            length = 255,
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
            dataType = "VARCHAR(255)",
            length = 255,
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
            nullable = true,
            comment = "同步状态，例如 PENDING / SUCCESS / FAILED。"
    )
    @TableField("sync_status")
    private AiChatToolSyncStatus syncStatus;

    /**
     * 是否启用。
     */
    @JdbcColumn(
            name = "enabled",
            dataType = "BOOLEAN",
            nullable = true,
            comment = "是否启用。"
    )
    @TableField("enabled")
    private Boolean enabled = Boolean.TRUE;

    /**
     * 备注。
     */
    @JdbcColumn(
            name = "remark",
            dataType = "VARCHAR(255)",
            length = 255,
            nullable = true,
            comment = "备注。"
    )
    @TableField("remark")
    private String remark;
}
