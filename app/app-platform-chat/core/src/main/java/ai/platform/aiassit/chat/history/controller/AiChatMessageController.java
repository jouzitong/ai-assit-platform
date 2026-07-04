package ai.platform.aiassit.chat.history.controller;

import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.service.AiChatMessageService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat/history/internal/message")
public class AiChatMessageController
        extends BaseController<AiChatMessageDTO, AiChatHistoryQueryRequest, AiChatMessageService> {

    private final AiChatMessageService service;

    public AiChatMessageController(AiChatMessageService service) {
        this.service = service;
    }

    @Override
    protected AiChatMessageService service() {
        return service;
    }
}
