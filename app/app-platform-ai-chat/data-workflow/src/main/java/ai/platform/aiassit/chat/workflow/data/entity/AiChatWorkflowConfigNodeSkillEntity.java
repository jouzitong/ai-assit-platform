package ai.platform.aiassit.chat.workflow.data.entity;

import ai.platform.aiassit.chat.workflow.data.enums.WorkflowNodeSkillPhase;
import ai.platform.aiassit.chat.workflow.data.entity.config.WorkflowNodeSkillRuntimeConfig;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;

/**
 * 流程配置节点 Skill 表。
 *
 * <p>把 skill 目录挂到某个流程配置节点上，并保存挂接阶段和节点内配置。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_chat_workflow_config_node_skill", autoResultMap = true)
public class AiChatWorkflowConfigNodeSkillEntity extends LogicalDeleteEntity {

    /**
     * 所属流程配置编码。
     */
    @TableField("config_code")
    private String configCode;

    /**
     * 所属节点编码。
     */
    @TableField("node_code")
    private String nodeCode;

    /**
     * Skill 编码。
     */
    @TableField("skill_code")
    private String skillCode;

    /**
     * 挂接阶段，例如 BEFORE_EXECUTE/AFTER_EXECUTE/REVIEW_OUTPUT。
     */
    @TableField("phase")
    private WorkflowNodeSkillPhase phase;

    /**
     * 排序。
     */
    @TableField("sort")
    private Integer sort;

    /**
     * 是否启用。
     */
    @TableField("enabled")
    private Boolean enabled = Boolean.TRUE;

    /**
     * 挂接配置 JSON。
     */
    @TableField(value = "config", typeHandler = JacksonTypeHandler.class)
    private WorkflowNodeSkillRuntimeConfig config;
}
