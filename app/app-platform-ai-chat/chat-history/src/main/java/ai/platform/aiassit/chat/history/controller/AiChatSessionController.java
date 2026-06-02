package ai.platform.aiassit.chat.history.controller;

import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.service.AiChatSessionService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat/history/internal/session")
public class AiChatSessionController
        extends BaseController<AiChatSessionDTO, AiChatHistoryQueryRequest, AiChatSessionService> {

    private final AiChatSessionService service;

    public AiChatSessionController(AiChatSessionService service) {
        this.service = service;
    }

    @Override
    protected AiChatSessionService service() {
        return service;
    }
}
