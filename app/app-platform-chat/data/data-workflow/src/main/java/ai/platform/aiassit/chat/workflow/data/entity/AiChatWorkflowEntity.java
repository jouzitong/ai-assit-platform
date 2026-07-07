package ai.platform.aiassit.chat.workflow.data.entity;

import ai.platform.aiassit.chat.workflow.data.entity.config.WorkflowCatalogConfig;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;

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
@TableName(value = "ai_chat_workflow", autoResultMap = true)
public class AiChatWorkflowEntity extends LogicalDeleteEntity {

    /**
     * 流程编码。
     */
    @TableField("code")
    private String code;

    /**
     * 流程名称。
     */
    @TableField("name")
    private String name;

    /**
     * 流程类型，例如 QUERY/CHAT/APP。
     */
    @TableField("type")
    private String type;

    /**
     * 是否启用。
     */
    @TableField("enabled")
    private Boolean enabled = Boolean.TRUE;

    /**
     * 流程目录配置 JSON。
     */
    @TableField(value = "config", typeHandler = JacksonTypeHandler.class)
    private WorkflowCatalogConfig config;
}
