package ai.platform.aiassit.conversation.service;

import ai.platform.aiassit.conversation.dto.conversation.ConversationHistoryPageResponse;
import ai.platform.aiassit.conversation.dto.conversation.ConversationHistoryWindowResponse;

public interface ConversationHistoryService {

    ConversationHistoryPageResponse page(
            String tenantId, Long userId, String sessionCode, String beforeCursor, int limit);

    ConversationHistoryWindowResponse window(
            String tenantId, Long userId, String sessionCode, String aroundRoundCode);
}
