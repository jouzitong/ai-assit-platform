package ai.platform.aiassit.conversation.dto.conversation;

import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ConversationDetailResponse {

    private AiChatSessionDTO session;

    private List<ConversationRoundDetailVO> rounds = new ArrayList<>();
}
