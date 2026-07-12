package ai.platform.aiassit.chat.workflow.data.entity;

import ai.platform.aiassit.chat.workflow.data.entity.config.AiNodeMessageConfig;
import ai.platform.aiassit.chat.workflow.data.entity.config.AiNodeOutputConfig;
import ai.platform.aiassit.chat.workflow.data.enums.AiExecuteType;
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
 * 节点目录表。
 *
 * <p>独立维护可配置的 AI 执行节点定义，不要求节点必须绑定某个流程。</p>
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
    @JdbcColumn(name = "code", unique = true, comment = "节点编码。")
    @TableField("code")
    private String code;

    /**
     * 节点名称。
     */
    @JdbcColumn(name = "name", comment = "节点名称。")
    @TableField("name")
    private String name;

    /**
     * 节点说明。
     */
    @JdbcColumn(name = "desc", comment = "节点说明。")
    @TableField("`desc`")
    private String desc;

    /**
     * AI 执行类型。
     */
    @JdbcColumn(name = "execute_type", comment = "AI 执行类型。")
    @TableField("execute_type")
    private AiExecuteType executeType;

    /**
     * 指定执行模型编码。
     */
    @JdbcColumn(name = "model_code", comment = "指定执行模型编码。")
    @TableField("model_code")
    private String modelCode;

    /**
     * 关联 skill 编码列表。
     */
    @JdbcColumn(name = "skill_refs", comment = "关联 skill 编码列表。")
    @TableField(value = "skill_refs", typeHandler = JacksonTypeHandler.class)
    private List<String> skillRefs = new ArrayList<>();

    /**
     * 关联 tool 编码列表。
     */
    @JdbcColumn(name = "tool_refs", comment = "关联 tool 编码列表。")
    @TableField(value = "tool_refs", typeHandler = JacksonTypeHandler.class)
    private List<String> toolRefs = new ArrayList<>();

    /**
     * 关联知识库编码列表。
     */
    @JdbcColumn(name = "kb_refs", comment = "关联知识库编码列表。")
    @TableField(value = "kb_refs", typeHandler = JacksonTypeHandler.class)
    private List<String> kbRefs = new ArrayList<>();

    /**
     * 输入消息配置。
     */
    @JdbcColumn(name = "input_config", comment = "输入消息配置。")
    @TableField(value = "input_config", typeHandler = JacksonTypeHandler.class)
    private List<AiNodeMessageConfig> inputConfig = new ArrayList<>();

    /**
     * 输出配置。
     */
    @JdbcColumn(name = "output_config", comment = "输出配置。")
    @TableField(value = "output_config", typeHandler = JacksonTypeHandler.class)
    private AiNodeOutputConfig outputConfig;

    /**
     * 是否启用。
     */
    @JdbcColumn(name = "enabled", comment = "是否启用。")
    @TableField("enabled")
    private Boolean enabled = Boolean.TRUE;

    /**
     * 备注。
     */
    @JdbcColumn(name = "remark", comment = "备注。")
    @TableField("remark")
    private String remark;
}
