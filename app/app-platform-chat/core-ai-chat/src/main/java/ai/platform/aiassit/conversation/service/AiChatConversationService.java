package ai.platform.aiassit.conversation.service;

import ai.platform.aiassit.conversation.dto.conversation.AiChatConversationDetailResponse;
import ai.platform.aiassit.conversation.dto.conversation.AiChatConversationPinRequest;
import ai.platform.aiassit.conversation.dto.conversation.AiChatConversationQueryRequest;
import ai.platform.aiassit.conversation.dto.conversation.AiChatConversationRenameRequest;
import ai.platform.aiassit.conversation.dto.conversation.AiChatConversationCreateRequest;
import ai.platform.aiassit.conversation.dto.conversation.AiChatConversationDeleteRequest;
import ai.platform.aiassit.conversation.dto.conversation.AiChatConversationDetailRequest;
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
