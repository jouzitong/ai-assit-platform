package ai.platform.aiassit.chat.core.query.dto;

import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiChatConversationDetailResponse {

    private AiChatSessionDTO session;

    private List<AiChatRoundDTO> rounds = new ArrayList<>();

    private List<AiChatMessageDTO> messages = new ArrayList<>();
}
