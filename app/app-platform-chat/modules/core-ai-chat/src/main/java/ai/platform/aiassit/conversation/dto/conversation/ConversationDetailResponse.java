package ai.platform.aiassit.conversation.dto.conversation;

import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ConversationDetailResponse {

    private ConversationSessionDTO session;

    private List<ConversationRoundDetailVO> rounds = new ArrayList<>();
}
