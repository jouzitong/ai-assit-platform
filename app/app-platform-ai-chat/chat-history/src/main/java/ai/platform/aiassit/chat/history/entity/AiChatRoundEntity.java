package ai.platform.aiassit.chat.history.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;

/**
 * AI 对话轮次实体。
 *
 * <p>用于记录一次会话中的单轮对话执行信息。一个会话可以包含多个对话轮次，
 * 每个轮次通常对应一次用户提问及其后续的 AI 分析、工具调用、结果生成等过程。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ai_chat_round")
public class AiChatRoundEntity extends LogicalDeleteEntity {

    /**
     * 对话轮次唯一编码。
     */
    @TableField("round_code")
    private String roundCode;

    /**
     * 轮次类型，用于区分普通问答、重试、追问、工具执行、流程节点执行等不同轮次场景。
     */
    @TableField("round_type")
    private String roundType;

    /**
     * 父轮次编码，用于建立轮次之间的上下文或派生关系。
     */
    @TableField("parent_round_code")
    private String parentRoundCode;

    /**
     * 会话编码，用于标识当前轮次所属的完整会话。
     */
    @TableField("session_code")
    private String sessionCode;

    /**
     * 用户 ID，用于标识当前轮次所属用户。
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 模型配置编码，表示当前轮次期望使用的模型配置。
     */
    @TableField("model_code")
    private String modelCode;

    /**
     * 实际调用的模型名称，用于记录最终真实执行的模型。
     */
    @TableField("actual_model")
    private String actualModel;

    /**
     * 轮次状态，例如处理中、成功、失败、取消。
     */
    @TableField("status")
    private String status;
}
