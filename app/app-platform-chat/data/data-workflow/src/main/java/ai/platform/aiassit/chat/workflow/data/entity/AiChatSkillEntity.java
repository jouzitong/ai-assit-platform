package ai.platform.aiassit.chat.workflow.data.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill 目录表。
 *
 * <p>独立维护面向 Agent 的 skill 规则文档及关联 tool 引用。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_chat_skill", autoResultMap = true)
public class AiChatSkillEntity extends LogicalDeleteEntity {

    /**
     * Skill 编码。
     */
    @JdbcColumn(
            name = "code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            unique = true,
            comment = "Skill 编码。"
    )
    @TableField("code")
    private String code;

    /**
     * Skill 名称。
     */
    @JdbcColumn(
            name = "name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "Skill 名称。"
    )
    @TableField("name")
    private String name;

    /**
     * Skill 简要说明。
     */
    @JdbcColumn(
            name = "desc",
            dataType = "VARCHAR(1024)",
            length = 1024,
            nullable = true,
            comment = "Skill 简要说明。"
    )
    @TableField("`desc`")
    private String desc;

    /**
     * Skill Markdown 规则内容。
     */
    @JdbcColumn(
            name = "content",
            dataType = "TEXT",
            nullable = true,
            comment = "Skill Markdown 规则内容预览。"
    )
    @TableField("content")
    private String content;

    /**
     * Skill 关联的 tool 编码列表。
     */
    @JdbcColumn(
            name = "tool_refs",
            dataType = "JSON",
            nullable = true,
            comment = "Skill 关联的 tool 编码列表。"
    )
    @TableField(value = "tool_refs", typeHandler = JacksonTypeHandler.class)
    private List<String> toolRefs = new ArrayList<>();

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
