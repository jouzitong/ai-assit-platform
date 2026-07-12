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
 * 代表AI聊天会话中生成的工件实体类。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ai_chat_artifact")
public class AiChatArtifactEntity extends LogicalDeleteEntity {

    /**
     * 工件的唯一代码。
     */
    @JdbcColumn(name = "artifact_code", unique = true, comment = "工件的唯一代码。")
    @TableField("artifact_code")
    private String artifactCode;

    /**
     * 工件所属会话的代码。
     *
     * @see AiChatSessionEntity#getSessionCode() sessionCode
     */
    @JdbcColumn(name = "session_code", comment = "工件所属会话的代码。")
    @TableField("session_code")
    private String sessionCode;

    /**
     * 会话中的轮次代码。
     *
     * @see AiChatRoundEntity#getRoundCode() roundCode
     */
    @JdbcColumn(name = "round_code", comment = "会话中的轮次代码。")
    @TableField("round_code")
    private String roundCode;

    /**
     * 生成工件的用户ID。
     */
    @JdbcColumn(name = "user_id", comment = "生成工件的用户ID。")
    @TableField("user_id")
    private Long userId;

    /**
     * 相关消息的代码。
     *
     * @see AiChatMessageEntity#getMessageCode() messageCode
     */
    @JdbcColumn(name = "related_message_code", comment = "相关消息的代码。")
    @TableField("related_message_code")
    private String relatedMessageCode;

    /**
     * 工件的类型（例如，图片、文本、文件）。
     */
    @JdbcColumn(name = "artifact_type", comment = "工件的类型（例如，图片、文本、文件）。")
    @TableField("artifact_type")
    private String artifactType;

    /**
     * 生成工件的会话阶段。
     */
    @JdbcColumn(name = "stage", comment = "生成工件的会话阶段。")
    @TableField("stage")
    private String stage;

    /**
     * 生成工件的生产者类型。
     */
    @JdbcColumn(name = "producer_type", comment = "生成工件的生产者类型。")
    @TableField("producer_type")
    private String producerType;

    /**
     * 表示工件是否可见的标志。
     */
    @JdbcColumn(name = "visible_flag", comment = "表示工件是否可见的标志。")
    @TableField("visible_flag")
    private Boolean visibleFlag;

    /**
     * 工件的标题。
     */
    @JdbcColumn(name = "title", comment = "工件的标题。")
    @TableField("title")
    private String title;

    /**
     * 工件的内容。
     */
    @JdbcColumn(name = "content", comment = "工件的内容。")
    @TableField("content")
    private String content;

    /**
     * 内容的格式（例如，JSON、纯文本）。
     */
    @JdbcColumn(name = "content_format", comment = "内容的格式（例如，JSON、纯文本）。")
    @TableField("content_format")
    private String contentFormat;

    /**
     * 工件的状态（例如，活动、已删除）。
     */
    @JdbcColumn(name = "status", comment = "工件的状态（例如，活动、已删除）。")
    @TableField("status")
    private String status;

    /**
     * 工件在会话中的序列号。
     */
    @JdbcColumn(name = "seq_no", comment = "工件在会话中的序列号。")
    @TableField("seq_no")
    private Integer seqNo;

    /**
     * 工件的附加JSON数据。
     */
    @JdbcColumn(name = "ext_json", comment = "工件的附加JSON数据。")
    @TableField("ext_json")
    private String extJson;
}
