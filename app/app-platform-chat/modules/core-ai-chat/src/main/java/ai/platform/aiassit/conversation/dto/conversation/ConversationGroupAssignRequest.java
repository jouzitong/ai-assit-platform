package ai.platform.aiassit.conversation.dto.conversation;

import lombok.Data;

/** 将会话移动到分组或未分组的请求。 */
@Data
public class ConversationGroupAssignRequest {

    private String sessionCode;

    /** 为空表示移动到未分组。 */
    private String groupCode;
}
