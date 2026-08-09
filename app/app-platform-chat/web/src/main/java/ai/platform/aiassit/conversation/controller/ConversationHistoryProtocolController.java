package ai.platform.aiassit.conversation.controller;

import ai.platform.aiassit.conversation.dto.conversation.ConversationHistoryPageResponse;
import ai.platform.aiassit.conversation.dto.conversation.ConversationHistoryWindowResponse;
import ai.platform.aiassit.conversation.service.ConversationHistoryService;
import ai.platform.aiassit.conversation.support.ConversationRequestContextResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cursor-based persisted history APIs used by context-source navigation. */
@RestController
@RequestMapping("/api/chat/sessions")
public class ConversationHistoryProtocolController {

    private final ConversationHistoryService historyService;
    private final ConversationRequestContextResolver contextResolver;

    public ConversationHistoryProtocolController(ConversationHistoryService historyService,
                                                 ConversationRequestContextResolver contextResolver) {
        this.historyService = historyService;
        this.contextResolver = contextResolver;
    }

    @GetMapping("/{sessionCode}/rounds")
    public ConversationHistoryPageResponse page(@PathVariable String sessionCode,
                                                @RequestParam(required = false) String before,
                                                @RequestParam(defaultValue = "20") int limit) {
        return historyService.page(contextResolver.currentTenantId(), contextResolver.currentUserId(),
                sessionCode, before, limit);
    }

    @GetMapping("/{sessionCode}/rounds/window")
    public ConversationHistoryWindowResponse window(@PathVariable String sessionCode,
                                                    @RequestParam String aroundRoundCode) {
        return historyService.window(contextResolver.currentTenantId(), contextResolver.currentUserId(),
                sessionCode, aroundRoundCode);
    }
}
