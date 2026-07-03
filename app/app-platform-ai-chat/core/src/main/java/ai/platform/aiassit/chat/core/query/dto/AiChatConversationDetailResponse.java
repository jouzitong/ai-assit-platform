package ai.platform.aiassit.chat.core.query.dto;

import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiChatConversationDetailResponse {

    private AiChatSessionDTO session;

    private List<AiChatConversationRoundDetailVO> rounds = new ArrayList<>();
}
