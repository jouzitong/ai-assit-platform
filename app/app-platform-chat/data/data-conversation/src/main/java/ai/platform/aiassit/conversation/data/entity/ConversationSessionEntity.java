package ai.platform.aiassit.conversation.data.entity;

import ai.platform.aiassit.conversation.data.enums.ConversationBusinessType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

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
@TableName("conversation_session")
public class ConversationSessionEntity extends LogicalDeleteEntity {

    /**
     * 会话唯一编码。
     */
    @JdbcColumn(
            name = "session_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            unique = true,
            comment = "会话编码"
    )
    @TableField("session_code")
    private String sessionCode;

    /**
     * 用户 ID，用于标识该会话所属的用户。
     */
    @JdbcColumn(
            name = "user_id",
            dataType = "BIGINT",
            nullable = false,
            defaultValue = "0",
            comment = "用户ID"
    )
    @TableField("user_id")
    private Long userId;

    /** 所属分组编码；为空表示未分组。 */
    @JdbcColumn(
            name = "group_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = true,
            comment = "所属分组编码，NULL表示未分组"
    )
    @TableField("group_code")
    private String groupCode;

    /**
     * 业务类型，用于区分不同业务场景下的 AI 对话，例如普通对话、智能问数、流程编排等。
     */
    @JdbcColumn(
            name = "business_type",
            dataType = "INT",
            nullable = true,
            comment = "业务类型"
    )
    @TableField("business_type")
    private ConversationBusinessType businessType;

    /**
     * 会话名称，通常用于前端展示会话标题。
     */
    @JdbcColumn(
            name = "session_name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = true,
            comment = "会话名称"
    )
    @TableField("session_name")
    private String sessionName;

    /**
     * 是否置顶。
     */
    @JdbcColumn(
            name = "pinned",
            dataType = "TINYINT",
            nullable = false,
            defaultValue = "0",
            comment = "是否置顶"
    )
    @TableField("pinned")
    private Boolean pinned = Boolean.FALSE;
}
