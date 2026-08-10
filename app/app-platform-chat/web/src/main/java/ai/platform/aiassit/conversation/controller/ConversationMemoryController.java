package ai.platform.aiassit.conversation.controller;

import ai.platform.aiassit.conversation.memory.ConversationMemoryManagementService;
import ai.platform.aiassit.conversation.support.ConversationRequestContextResolver;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryConfirmRequest;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryCorrectionRequest;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryCreateRequest;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryListResponse;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemoryOperationResponse;
import ai.platform.aiassit.service.ai.api.memory.dto.ConversationMemorySessionPolicyRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Current-user Memory controls. Provider identifiers are accepted only through encrypted
 * references issued by the server and are revalidated against the authenticated owner.
 */
@RestController
@RequestMapping("/api/chat/memories")
public class ConversationMemoryController {

    private final ConversationMemoryManagementService memoryService;
    private final ConversationRequestContextResolver contextResolver;

    public ConversationMemoryController(ConversationMemoryManagementService memoryService,
                                        ConversationRequestContextResolver contextResolver) {
        this.memoryService = memoryService;
        this.contextResolver = contextResolver;
    }

    @GetMapping("/long-term")
    public ConversationMemoryListResponse longTermMemories() {
        return memoryService.longTermMemories(tenantId(), userId());
    }

    @PostMapping("/long-term")
    public ConversationMemoryOperationResponse createLongTerm(
            @RequestBody ConversationMemoryCreateRequest request) {
        return memoryService.createLongTerm(tenantId(), userId(), request);
    }

    @PostMapping("/long-term/clear")
    public ConversationMemoryOperationResponse clearLongTerm(
            @RequestBody ConversationMemoryConfirmRequest request) {
        return memoryService.clearLongTerm(tenantId(), userId(), request);
    }

    @PostMapping("/{memoryRef}/disable")
    public ConversationMemoryOperationResponse disable(@PathVariable String memoryRef) {
        return memoryService.disable(tenantId(), userId(), memoryRef);
    }

    @PostMapping("/{memoryRef}/restore")
    public ConversationMemoryOperationResponse restore(@PathVariable String memoryRef) {
        return memoryService.restore(tenantId(), userId(), memoryRef);
    }

    @PostMapping("/{memoryRef}/correct")
    public ConversationMemoryOperationResponse correct(@PathVariable String memoryRef,
                                                        @RequestBody ConversationMemoryCorrectionRequest request) {
        return memoryService.correct(tenantId(), userId(), memoryRef, request);
    }

    @PostMapping("/{memoryRef}/promote")
    public ConversationMemoryOperationResponse promote(@PathVariable String memoryRef,
                                                        @RequestBody ConversationMemoryConfirmRequest request) {
        return memoryService.promote(tenantId(), userId(), memoryRef, request);
    }

    @PostMapping("/{memoryRef}/exclude-from-session")
    public ConversationMemoryOperationResponse excludeFromSession(
            @PathVariable String memoryRef,
            @RequestBody ConversationMemorySessionPolicyRequest request) {
        return memoryService.excludeFromSession(tenantId(), userId(), memoryRef, request);
    }

    @DeleteMapping("/{memoryRef}")
    public ConversationMemoryOperationResponse forget(@PathVariable String memoryRef) {
        return memoryService.forget(tenantId(), userId(), memoryRef);
    }

    private String tenantId() {
        return contextResolver.currentTenantId();
    }

    private Long userId() {
        return contextResolver.currentUserId();
    }
}
