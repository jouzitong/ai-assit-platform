package ai.platform.aiassit.conversation.dto.conversation;

import ai.platform.aiassit.conversation.data.enums.ConversationBusinessType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationSessionVO {

    private String sessionCode;

    private Long userId;

    private ConversationBusinessType businessType;

    private String sessionName;

    private Boolean pinned = Boolean.FALSE;

    private LocalDateTime updateTime;
}
