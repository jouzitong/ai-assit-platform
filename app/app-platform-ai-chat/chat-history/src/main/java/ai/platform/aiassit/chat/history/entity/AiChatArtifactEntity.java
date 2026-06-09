package ai.platform.aiassit.chat.history.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;

/**
 * 代表AI聊天会话中生成的工件实体类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ai_chat_artifact")
public class AiChatArtifactEntity extends LogicalDeleteEntity {

    /**
     * 工件的唯一代码。
     */
    @TableField("artifact_code")
    private String artifactCode;

    /**
     * 工件所属会话的代码。
     */
    @TableField("session_code")
    private String sessionCode;

    /**
     * 会话中的轮次代码。
     */
    @TableField("round_code")
    private String roundCode;

    /**
     * 生成工件的用户ID。
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 相关消息的代码。
     */
    @TableField("related_message_code")
    private String relatedMessageCode;

    /**
     * 工件的类型（例如，图片、文本、文件）。
     */
    @TableField("artifact_type")
    private String artifactType;

    /**
     * 生成工件的会话阶段。
     */
    @TableField("stage")
    private String stage;

    /**
     * 生成工件的生产者类型。
     */
    @TableField("producer_type")
    private String producerType;

    /**
     * 表示工件是否可见的标志。
     */
    @TableField("visible_flag")
    private Boolean visibleFlag;

    /**
     * 工件的标题。
     */
    @TableField("title")
    private String title;

    /**
     * 工件的内容。
     */
    @TableField("content")
    private String content;

    /**
     * 内容的格式（例如，JSON、纯文本）。
     */
    @TableField("content_format")
    private String contentFormat;

    /**
     * 工件的状态（例如，活动、已删除）。
     */
    @TableField("status")
    private String status;

    /**
     * 工件在会话中的序列号。
     */
    @TableField("seq_no")
    private Integer seqNo;

    /**
     * 工件的附加JSON数据。
     */
    @TableField("ext_json")
    private String extJson;
}
