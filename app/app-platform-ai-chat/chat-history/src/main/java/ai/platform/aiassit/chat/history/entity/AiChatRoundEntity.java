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
@TableName("ai_chat_round")
public class AiChatRoundEntity extends LogicalDeleteEntity {

    @TableField("round_code")
    private String roundCode;

    @TableField("round_type")
    private String roundType;

    @TableField("parent_round_code")
    private String parentRoundCode;

    @TableField("session_code")
    private String sessionCode;

    @TableField("user_id")
    private Long userId;

    @TableField("model_code")
    private String modelCode;

    @TableField("actual_model")
    private String actualModel;

    @TableField("status")
    private String status;
}
