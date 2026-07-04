package ai.platform.aiassit.chat.history.controller;

import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.service.AiChatRoundService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat/history/internal/round")
public class AiChatRoundController
        extends BaseController<AiChatRoundDTO, AiChatHistoryQueryRequest, AiChatRoundService> {

    private final AiChatRoundService service;

    public AiChatRoundController(AiChatRoundService service) {
        this.service = service;
    }

    @Override
    protected AiChatRoundService service() {
        return service;
    }
}
