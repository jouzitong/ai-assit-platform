package ai.platform.aiassit.conversation.dto.conversation;

import ai.platform.aiassit.conversation.data.enums.ConversationBusinessType;
import lombok.Data;

@Data
public class ConversationQueryRequest {

    private Long userId;

    private String sessionCode;

    private ConversationBusinessType businessType;
}
