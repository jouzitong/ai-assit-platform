package ai.platform.aiassit.chat.workflow.data.entity;

import ai.platform.aiassit.chat.workflow.data.entity.config.WorkflowSkillCatalogConfig;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;

/**
 * Skill 目录表。
 *
 * <p>独立维护系统实现过的 skill 能力，供流程配置时选择挂接。</p>
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
    @TableField("code")
    private String code;

    /**
     * Skill 名称。
     */
    @TableField("name")
    private String name;

    /**
     * Skill 类型或分类。
     */
    @TableField("type")
    private String type;

    /**
     * 是否启用。
     */
    @TableField("enabled")
    private Boolean enabled = Boolean.TRUE;

    /**
     * Skill 目录配置 JSON。
     */
    @TableField(value = "config", typeHandler = JacksonTypeHandler.class)
    private WorkflowSkillCatalogConfig config;
}
