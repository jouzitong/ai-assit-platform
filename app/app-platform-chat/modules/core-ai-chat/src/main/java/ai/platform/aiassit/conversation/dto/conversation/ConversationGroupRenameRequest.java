package ai.platform.aiassit.conversation.dto.conversation;

import lombok.Data;

/** 重命名会话分组请求。 */
@Data
public class ConversationGroupRenameRequest {

    private String groupCode;

    private String groupName;
}
