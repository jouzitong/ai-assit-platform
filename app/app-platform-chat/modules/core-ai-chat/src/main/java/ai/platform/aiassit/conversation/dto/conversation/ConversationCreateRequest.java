package ai.platform.aiassit.conversation.dto.conversation;

import ai.platform.aiassit.conversation.data.enums.ConversationBusinessType;
import lombok.Data;

@Data
public class ConversationCreateRequest {

    private Long userId;

    private String sessionName;

    private ConversationBusinessType businessType;
}
