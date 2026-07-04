package ai.platform.aiassit.chat.workflow.data.controller;

import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatWorkflowDTO;
import ai.platform.aiassit.chat.workflow.data.entity.req.AiChatWorkflowQueryRequest;
import ai.platform.aiassit.chat.workflow.data.service.AiChatWorkflowService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat/workflow/internal/workflow")
public class AiChatWorkflowController
        extends BaseController<AiChatWorkflowDTO, AiChatWorkflowQueryRequest, AiChatWorkflowService> {

    private final AiChatWorkflowService service;

    public AiChatWorkflowController(AiChatWorkflowService service) {
        this.service = service;
    }

    @Override
    protected AiChatWorkflowService service() {
        return service;
    }
}
