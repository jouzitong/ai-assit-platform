package ai.platform.aiassit.chat.workflow.data.entity;

import ai.platform.aiassit.chat.workflow.data.entity.config.WorkflowNodeCatalogConfig;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;

/**
 * 节点目录表。
 *
 * <p>独立维护系统已实现的节点能力，不要求节点必须绑定某个流程。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_chat_node", autoResultMap = true)
public class AiChatNodeEntity extends LogicalDeleteEntity {

    /**
     * 节点编码。
     */
    @TableField("code")
    private String code;

    /**
     * 节点名称。
     */
    @TableField("name")
    private String name;

    /**
     * 节点实现类型，例如 QueryPlanningNode。
     */
    @TableField("type")
    private String type;

    /**
     * 是否启用。
     */
    @TableField("enabled")
    private Boolean enabled = Boolean.TRUE;

    /**
     * 节点目录配置 JSON。
     * 可放默认输入输出定义、默认提示模板、默认执行参数等。
     */
    @TableField(value = "config", typeHandler = JacksonTypeHandler.class)
    private WorkflowNodeCatalogConfig config;
}
