package ai.platform.aiassit.chat.history.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ai_chat_message")
public class AiChatMessageEntity extends LogicalDeleteEntity {

    @TableField("message_code")
    private String messageCode;

    @TableField("round_code")
    private String roundCode;

    @TableField("session_code")
    private String sessionCode;

    @TableField("role")
    private String role;

    @TableField("actor_type")
    private String actorType;

    @TableField("message_type")
    private String messageType;

    @TableField("display_level")
    private String displayLevel;

    @TableField("content_format")
    private String contentFormat;

    @TableField("parent_message_code")
    private String parentMessageCode;

    @TableField("source_message_code")
    private String sourceMessageCode;

    @TableField("status")
    private String status;

    @TableField("content")
    private String content;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField("ext_json")
    private String extJson;
}
