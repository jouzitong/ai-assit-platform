package ai.platform.aiassit.conversation.dto.conversation;

import ai.platform.aiassit.conversation.data.enums.ConversationBusinessType;
import lombok.Data;

@Data
public class ConversationQueryRequest {

    private Long userId;

    private String sessionCode;

    /** 会话分组编码；为空表示不按分组筛选。 */
    private String groupCode;

    private ConversationBusinessType businessType;
}
