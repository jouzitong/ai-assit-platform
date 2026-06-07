package ai.platform.aiassit.chat.core.conversation.service;

import ai.platform.aiassit.chat.core.query.dto.AiChatConversationDetailResponse;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationPinRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationQueryRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationRenameRequest;
import ai.platform.aiassit.chat.core.query.req.AiChatConversationCreateRequest;
import ai.platform.aiassit.chat.core.query.req.AiChatConversationDeleteRequest;
import ai.platform.aiassit.chat.core.query.req.AiChatConversationDetailRequest;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;

import java.util.List;

public interface AiChatConversationService {

    List<AiChatSessionDTO> listConversations(AiChatConversationQueryRequest request);

    AiChatConversationDetailResponse detailConversation(AiChatConversationDetailRequest request);

    AiChatConversationDetailResponse createConversation(AiChatConversationCreateRequest request);

    AiChatSessionDTO renameConversation(AiChatConversationRenameRequest request);

    AiChatSessionDTO pinConversation(AiChatConversationPinRequest request);

    Boolean deleteConversation(AiChatConversationDeleteRequest request);
}
