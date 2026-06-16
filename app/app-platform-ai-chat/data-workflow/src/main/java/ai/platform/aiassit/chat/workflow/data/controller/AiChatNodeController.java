package ai.platform.aiassit.chat.workflow.data.controller;

import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatNodeDTO;
import ai.platform.aiassit.chat.workflow.data.entity.req.AiChatNodeQueryRequest;
import ai.platform.aiassit.chat.workflow.data.service.AiChatNodeService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat/workflow/internal/node")
public class AiChatNodeController
        extends BaseController<AiChatNodeDTO, AiChatNodeQueryRequest, AiChatNodeService> {

    private final AiChatNodeService service;

    public AiChatNodeController(AiChatNodeService service) {
        this.service = service;
    }

    @Override
    protected AiChatNodeService service() {
        return service;
    }
}
