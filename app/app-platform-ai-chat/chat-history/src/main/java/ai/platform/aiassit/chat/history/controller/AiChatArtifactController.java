package ai.platform.aiassit.chat.history.controller;

import ai.platform.aiassit.chat.history.entity.dto.AiChatArtifactDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.service.AiChatArtifactService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat/history/internal/artifact")
public class AiChatArtifactController
        extends BaseController<AiChatArtifactDTO, AiChatHistoryQueryRequest, AiChatArtifactService> {

    private final AiChatArtifactService service;

    public AiChatArtifactController(AiChatArtifactService service) {
        this.service = service;
    }

    @Override
    protected AiChatArtifactService service() {
        return service;
    }
}
