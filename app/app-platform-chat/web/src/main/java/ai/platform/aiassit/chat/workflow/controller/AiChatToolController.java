package ai.platform.aiassit.chat.workflow.controller;

import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatToolDTO;
import ai.platform.aiassit.chat.workflow.data.entity.req.AiChatToolQueryRequest;
import ai.platform.aiassit.chat.workflow.data.service.AiChatToolService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat/workflow/internal/tool")
public class AiChatToolController
        extends BaseController<AiChatToolDTO, AiChatToolQueryRequest, AiChatToolService> {

    private final AiChatToolService service;

    public AiChatToolController(AiChatToolService service) {
        this.service = service;
    }

    @Override
    protected AiChatToolService service() {
        return service;
    }
}
