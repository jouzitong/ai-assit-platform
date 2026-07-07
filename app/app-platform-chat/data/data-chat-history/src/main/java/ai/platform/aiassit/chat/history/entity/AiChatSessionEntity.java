package ai.platform.aiassit.chat.history.entity;

import ai.platform.aiassit.chat.history.enums.AiChatBusinessType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;

/**
 * AI 对话会话实体。
 *
 * <p>用于记录一次完整的 AI 对话会话信息。一个会话可以包含多个对话轮次和多条消息，
 * 通过 sessionCode 与轮次、消息等历史记录建立关联。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ai_chat_session")
public class AiChatSessionEntity extends LogicalDeleteEntity {

    /**
     * 会话唯一编码。
     */
    @TableField("session_code")
    private String sessionCode;

    /**
     * 用户 ID，用于标识该会话所属的用户。
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 业务类型，用于区分不同业务场景下的 AI 对话，例如普通对话、智能问数、流程编排等。
     */
    @TableField("business_type")
    private AiChatBusinessType businessType;

    /**
     * 会话名称，通常用于前端展示会话标题。
     */
    @TableField("session_name")
    private String sessionName;

    /**
     * 是否置顶。
     */
    @TableField("pinned")
    private Boolean pinned = Boolean.FALSE;
}
