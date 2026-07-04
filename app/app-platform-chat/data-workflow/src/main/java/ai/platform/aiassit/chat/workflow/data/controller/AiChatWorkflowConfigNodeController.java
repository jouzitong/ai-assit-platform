package ai.platform.aiassit.chat.workflow.data.controller;

import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatWorkflowConfigNodeDTO;
import ai.platform.aiassit.chat.workflow.data.entity.req.AiChatWorkflowConfigNodeQueryRequest;
import ai.platform.aiassit.chat.workflow.data.service.AiChatWorkflowConfigNodeService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat/workflow/internal/config-node")
public class AiChatWorkflowConfigNodeController
        extends BaseController<AiChatWorkflowConfigNodeDTO, AiChatWorkflowConfigNodeQueryRequest, AiChatWorkflowConfigNodeService> {

    private final AiChatWorkflowConfigNodeService service;

    public AiChatWorkflowConfigNodeController(AiChatWorkflowConfigNodeService service) {
        this.service = service;
    }

    @Override
    protected AiChatWorkflowConfigNodeService service() {
        return service;
    }
}
