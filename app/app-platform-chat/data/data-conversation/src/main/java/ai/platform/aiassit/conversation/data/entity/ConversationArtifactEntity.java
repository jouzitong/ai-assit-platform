package ai.platform.aiassit.conversation.data.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

/**
 * 代表会话中生成的产物实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("conversation_artifact")
public class ConversationArtifactEntity extends LogicalDeleteEntity {

    /**
     * 工件的唯一代码。
     */
    @JdbcColumn(
            name = "artifact_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            unique = true,
            comment = "产物编码"
    )
    @TableField("artifact_code")
    private String artifactCode;

    /**
     * 工件所属会话的代码。
     *
     * @see ConversationSessionEntity#getSessionCode() sessionCode
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
     * 会话中的轮次代码。
     *
     * @see ConversationRoundEntity#getRoundCode() roundCode
     */
    @JdbcColumn(
            name = "round_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = true,
            comment = "轮次编码"
    )
    @TableField("round_code")
    private String roundCode;

    /**
     * 生成工件的用户ID。
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

    /**
     * 相关消息的代码。
     *
     * @see ConversationMessageEntity#getMessageCode() messageCode
     */
    @JdbcColumn(
            name = "related_message_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = true,
            comment = "关联消息编码"
    )
    @TableField("related_message_code")
    private String relatedMessageCode;

    /**
     * 工件的类型（例如，图片、文本、文件）。
     */
    @JdbcColumn(
            name = "artifact_type",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = false,
            comment = "产物类型"
    )
    @TableField("artifact_type")
    private String artifactType;

    /**
     * 生成工件的会话阶段。
     */
    @JdbcColumn(
            name = "stage",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = false,
            comment = "流程阶段"
    )
    @TableField("stage")
    private String stage;

    /**
     * 生成工件的生产者类型。
     */
    @JdbcColumn(
            name = "producer_type",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = false,
            defaultValue = "'SYSTEM'",
            comment = "产物生产者"
    )
    @TableField("producer_type")
    private String producerType;

    /**
     * 表示工件是否可见的标志。
     */
    @JdbcColumn(
            name = "visible_flag",
            dataType = "TINYINT",
            nullable = false,
            defaultValue = "0",
            comment = "是否对前端可见"
    )
    @TableField("visible_flag")
    private Boolean visibleFlag;

    /**
     * 工件的标题。
     */
    @JdbcColumn(
            name = "title",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = true,
            comment = "产物标题"
    )
    @TableField("title")
    private String title;

    /**
     * 工件的内容。
     */
    @JdbcColumn(
            name = "content",
            dataType = "MEDIUMTEXT",
            nullable = false,
            comment = "产物内容"
    )
    @TableField("content")
    private String content;

    /**
     * 内容的格式（例如，JSON、纯文本）。
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
     * 工件的状态（例如，活动、已删除）。
     */
    @JdbcColumn(
            name = "status",
            dataType = "VARCHAR(32)",
            length = 32,
            nullable = false,
            defaultValue = "'SUCCESS'",
            comment = "产物状态"
    )
    @TableField("status")
    private String status;

    /**
     * 工件在会话中的序列号。
     */
    @JdbcColumn(
            name = "seq_no",
            dataType = "INT",
            nullable = false,
            defaultValue = "1",
            comment = "会话内顺序"
    )
    @TableField("seq_no")
    private Integer seqNo;

    /**
     * 工件的附加JSON数据。
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
