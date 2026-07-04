package ai.platform.aiassit.chat.workflow.data.controller;

import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatWorkflowConfigDTO;
import ai.platform.aiassit.chat.workflow.data.entity.req.AiChatWorkflowConfigQueryRequest;
import ai.platform.aiassit.chat.workflow.data.service.AiChatWorkflowConfigService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat/workflow/internal/config")
public class AiChatWorkflowConfigController
        extends BaseController<AiChatWorkflowConfigDTO, AiChatWorkflowConfigQueryRequest, AiChatWorkflowConfigService> {

    private final AiChatWorkflowConfigService service;

    public AiChatWorkflowConfigController(AiChatWorkflowConfigService service) {
        this.service = service;
    }

    @Override
    protected AiChatWorkflowConfigService service() {
        return service;
    }
}
