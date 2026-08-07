package ai.platform.aiassit.conversation.dto.conversation;

import lombok.Data;

import java.time.LocalDateTime;

/** 面向聊天页面的分组摘要。 */
@Data
public class ConversationGroupVO {

    private String groupCode;

    private Long userId;

    private String groupName;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
