package ai.platform.aiassit.chat.history.entity;

import ai.platform.aiassit.chat.history.entity.enums.AiChatBusinessType;
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
@TableName("ai_chat_session")
public class AiChatSessionEntity extends LogicalDeleteEntity {

    @TableField("session_code")
    private String sessionCode;

    @TableField("user_id")
    private Long userId;

    @TableField("business_type")
    private AiChatBusinessType businessType;

    @TableField("session_name")
    private String sessionName;

    @TableField("pinned")
    private Boolean pinned = Boolean.FALSE;
}
