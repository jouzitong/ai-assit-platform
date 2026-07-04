package ai.platform.aiassit.chat.workflow.data.controller;

import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatSkillDTO;
import ai.platform.aiassit.chat.workflow.data.entity.req.AiChatSkillQueryRequest;
import ai.platform.aiassit.chat.workflow.data.service.AiChatSkillService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat/workflow/internal/skill")
public class AiChatSkillController
        extends BaseController<AiChatSkillDTO, AiChatSkillQueryRequest, AiChatSkillService> {

    private final AiChatSkillService service;

    public AiChatSkillController(AiChatSkillService service) {
        this.service = service;
    }

    @Override
    protected AiChatSkillService service() {
        return service;
    }
}
