package ai.platform.aiassit.conversation.service;

import ai.platform.aiassit.conversation.dto.conversation.ConversationDetailResponse;
import ai.platform.aiassit.conversation.dto.conversation.ConversationPinRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationQueryRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationRenameRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationCreateRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationDeleteRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationDetailRequest;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;

import java.util.List;

public interface ConversationService {

    List<AiChatSessionDTO> listConversations(ConversationQueryRequest request);

    ConversationDetailResponse detailConversation(ConversationDetailRequest request);

    ConversationDetailResponse createConversation(ConversationCreateRequest request);

    AiChatSessionDTO renameConversation(ConversationRenameRequest request);

    AiChatSessionDTO pinConversation(ConversationPinRequest request);

    Boolean deleteConversation(ConversationDeleteRequest request);
}
