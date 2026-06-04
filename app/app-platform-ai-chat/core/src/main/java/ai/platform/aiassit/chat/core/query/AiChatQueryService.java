package ai.platform.aiassit.chat.core.query;

import ai.platform.aiassit.chat.api.dto.AiChatConversationCreateRequest;
import ai.platform.aiassit.chat.api.dto.AiChatConversationDetailRequest;
import ai.platform.aiassit.chat.api.dto.AiChatConversationDetailResponse;
import ai.platform.aiassit.chat.api.dto.AiChatConversationQueryRequest;
import ai.platform.aiassit.chat.api.dto.AiChatQueryRequest;
import ai.platform.aiassit.chat.api.dto.AiChatQueryResponse;
import ai.platform.aiassit.chat.history.entity.AiChatMessageEntity;
import ai.platform.aiassit.chat.history.entity.AiChatRoundEntity;
import ai.platform.aiassit.chat.history.entity.AiChatSessionEntity;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface AiChatQueryService {

    AiChatQueryResponse query(AiChatQueryRequest request);

    SseEmitter queryStream(AiChatQueryRequest request);

    List<AiChatSessionDTO> listConversations(AiChatConversationQueryRequest request);

    AiChatConversationDetailResponse detailConversation(AiChatConversationDetailRequest request);

    AiChatConversationDetailResponse createConversation(AiChatConversationCreateRequest request);
}
