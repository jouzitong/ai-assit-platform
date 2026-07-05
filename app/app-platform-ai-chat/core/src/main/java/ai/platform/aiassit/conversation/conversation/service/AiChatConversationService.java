package ai.platform.aiassit.conversation.conversation.service;

import ai.platform.aiassit.conversation.query.dto.AiChatConversationDetailResponse;
import ai.platform.aiassit.conversation.query.dto.AiChatConversationPinRequest;
import ai.platform.aiassit.conversation.query.dto.AiChatConversationQueryRequest;
import ai.platform.aiassit.conversation.query.dto.AiChatConversationRenameRequest;
import ai.platform.aiassit.conversation.query.req.AiChatConversationCreateRequest;
import ai.platform.aiassit.conversation.query.req.AiChatConversationDeleteRequest;
import ai.platform.aiassit.conversation.query.req.AiChatConversationDetailRequest;
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
