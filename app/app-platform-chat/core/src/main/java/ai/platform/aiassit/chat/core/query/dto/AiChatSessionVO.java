package ai.platform.aiassit.chat.core.query.dto;

import ai.platform.aiassit.chat.history.enums.AiChatBusinessType;
import lombok.Data;

@Data
public class AiChatSessionVO {

    private String sessionCode;

    private Long userId;

    private AiChatBusinessType businessType;

    private String sessionName;

    private Boolean pinned = Boolean.FALSE;
}
