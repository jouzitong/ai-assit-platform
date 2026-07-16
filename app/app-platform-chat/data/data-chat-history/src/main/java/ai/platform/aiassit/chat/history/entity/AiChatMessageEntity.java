package ai.platform.aiassit.chat.history.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

/**
 * AI 对话消息实体。
 *
 * <p>用于记录一次会话中的具体消息内容，包含用户输入、AI 回复、系统过程消息、工具调用结果等。
 * 消息通过 sessionCode 归属到会话，通过 roundCode 归属到对话轮次，并可通过 parentMessageCode/sourceMessageCode 建立消息之间的上下文关系。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ai_chat_message")
public class AiChatMessageEntity extends LogicalDeleteEntity {

    /**
     * 消息唯一编码。
     *
     * @see AiChatMessageEntity#getMessageCode() messageCode
     */
    @JdbcColumn(
            name = "message_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            unique = true,
            comment = "消息编码"
    )
    @TableField("message_code")
    private String messageCode;

    /**
     * 对话轮次编码，用于标识消息所属的某一轮问答。
     *
     * @see AiChatRoundEntity#getRoundCode() roundCode
     */
    @JdbcColumn(
            name = "round_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            comment = "轮次编码"
    )
    @TableField("round_code")
    private String roundCode;

    /**
     * 会话编码，用于标识消息所属的完整会话。
     */
    @JdbcColumn(
            name = "session_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            comment = "会话编码"
    )
    @TableField("session_code")
    private String sessionCode;

    /**
     * 消息角色，例如 user、assistant、system、tool。
     */
    @JdbcColumn(
            name = "role",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = false,
            comment = "角色：USER/ASSISTANT"
    )
    @TableField("role")
    private String role;

    /**
     * 消息参与者类型，用于区分真实用户、Agent、系统流程、工具等来源。
     */
    @JdbcColumn(
            name = "actor_type",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = false,
            defaultValue = "'HUMAN'",
            comment = "消息生产者类型"
    )
    @TableField("actor_type")
    private String actorType;

    /**
     * 消息类型，例如普通文本、思考过程、工具调用、工具结果、异常信息等。
     */
    @JdbcColumn(
            name = "message_type",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = false,
            defaultValue = "'USER_INPUT'",
            comment = "消息业务类型"
    )
    @TableField("message_type")
    private String messageType;

    /**
     * 展示级别，用于控制前端是否展示以及展示粒度，例如主消息、过程消息、调试消息。
     */
    @JdbcColumn(
            name = "display_level",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = false,
            defaultValue = "'VISIBLE'",
            comment = "展示层级"
    )
    @TableField("display_level")
    private String displayLevel;

    /**
     * 内容格式，例如 text、markdown、json、html。
     */
    @JdbcColumn(
            name = "content_format",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = false,
            defaultValue = "'PLAIN_TEXT'",
            comment = "内容格式"
    )
    @TableField("content_format")
    private String contentFormat;

    /**
     * 父消息编码，用于建立消息之间的层级关系。
     */
    @JdbcColumn(
            name = "parent_message_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = true,
            comment = "父消息编码"
    )
    @TableField("parent_message_code")
    private String parentMessageCode;

    /**
     * 来源消息编码，用于记录当前消息由哪条消息触发或生成。
     */
    @JdbcColumn(
            name = "source_message_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = true,
            comment = "源消息编码"
    )
    @TableField("source_message_code")
    private String sourceMessageCode;

    /**
     * 消息状态，例如生成中、成功、失败、取消。
     */
    @JdbcColumn(
            name = "status",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = false,
            defaultValue = "'SUCCESS'",
            comment = "消息状态"
    )
    @TableField("status")
    private String status;

    /**
     * 消息内容正文。
     */
    @JdbcColumn(
            name = "content",
            dataType = "MEDIUMTEXT",
            nullable = false,
            comment = "消息内容"
    )
    @TableField("content")
    private String content;

    /**
     * 排序号，用于控制同一会话或同一轮次内的消息展示顺序。
     */
    @JdbcColumn(
            name = "sort_no",
            dataType = "INT",
            nullable = false,
            defaultValue = "1",
            comment = "轮次内顺序"
    )
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 扩展信息 JSON，用于保存模型参数、工具调用参数、执行耗时、错误详情等非固定结构数据。
     */
    @JdbcColumn(
            name = "ext_json",
            dataType = "MEDIUMTEXT",
            nullable = true,
            comment = "扩展信息"
    )
    @TableField("ext_json")
    private String extJson;
}
