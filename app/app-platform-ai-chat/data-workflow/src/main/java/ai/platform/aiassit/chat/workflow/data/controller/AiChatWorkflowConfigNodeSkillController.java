package ai.platform.aiassit.chat.workflow.data.controller;

import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatWorkflowConfigNodeSkillDTO;
import ai.platform.aiassit.chat.workflow.data.entity.req.AiChatWorkflowConfigNodeSkillQueryRequest;
import ai.platform.aiassit.chat.workflow.data.service.AiChatWorkflowConfigNodeSkillService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat/workflow/internal/config-node-skill")
public class AiChatWorkflowConfigNodeSkillController
        extends BaseController<AiChatWorkflowConfigNodeSkillDTO, AiChatWorkflowConfigNodeSkillQueryRequest, AiChatWorkflowConfigNodeSkillService> {

    private final AiChatWorkflowConfigNodeSkillService service;

    public AiChatWorkflowConfigNodeSkillController(AiChatWorkflowConfigNodeSkillService service) {
        this.service = service;
    }

    @Override
    protected AiChatWorkflowConfigNodeSkillService service() {
        return service;
    }
}
