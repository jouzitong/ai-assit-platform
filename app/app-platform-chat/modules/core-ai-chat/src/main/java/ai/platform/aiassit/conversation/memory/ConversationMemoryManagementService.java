package ai.platform.aiassit.conversation.memory;

import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryConfirmRequest;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryContextResponse;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryCorrectionRequest;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryListResponse;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryOperationResponse;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemorySessionPolicyRequest;

public interface ConversationMemoryManagementService {

    ConversationMemoryContextResponse context(String tenantId, Long userId, String sessionCode);

    ConversationMemoryListResponse longTermMemories(String tenantId, Long userId);

    ConversationMemoryOperationResponse clearLongTerm(
            String tenantId, Long userId, ConversationMemoryConfirmRequest request);

    ConversationMemoryOperationResponse disable(String tenantId, Long userId, String memoryRef);

    ConversationMemoryOperationResponse restore(String tenantId, Long userId, String memoryRef);

    ConversationMemoryOperationResponse correct(
            String tenantId, Long userId, String memoryRef, ConversationMemoryCorrectionRequest request);

    ConversationMemoryOperationResponse promote(
            String tenantId, Long userId, String memoryRef, ConversationMemoryConfirmRequest request);

    ConversationMemoryOperationResponse excludeFromSession(
            String tenantId, Long userId, String memoryRef, ConversationMemorySessionPolicyRequest request);

    ConversationMemoryOperationResponse forget(String tenantId, Long userId, String memoryRef);
}
