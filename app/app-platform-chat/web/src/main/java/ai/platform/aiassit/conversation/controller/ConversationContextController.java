package ai.platform.aiassit.conversation.controller;

import ai.platform.aiassit.conversation.memory.ConversationMemoryManagementService;
import ai.platform.aiassit.conversation.support.ConversationRequestContextResolver;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryContextResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only, real-time view of the current conversation context assembled from RAGFlow. */
@RestController
@RequestMapping("/api/chat/sessions")
public class ConversationContextController {

    private final ConversationMemoryManagementService memoryService;
    private final ConversationRequestContextResolver contextResolver;

    public ConversationContextController(ConversationMemoryManagementService memoryService,
                                         ConversationRequestContextResolver contextResolver) {
        this.memoryService = memoryService;
        this.contextResolver = contextResolver;
    }

    @GetMapping("/{sessionCode}/context")
    public ConversationMemoryContextResponse context(@PathVariable String sessionCode) {
        return memoryService.context(
                contextResolver.currentTenantId(), contextResolver.currentUserId(), sessionCode);
    }
}
