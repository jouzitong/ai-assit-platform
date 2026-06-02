package ai.platform.aiassit.chat.meta.controller;

import ai.platform.aiassit.chat.meta.entity.dto.AiModelCredentialDTO;
import ai.platform.aiassit.chat.meta.entity.req.AiMetaQueryRequest;
import ai.platform.aiassit.chat.meta.service.AiModelCredentialService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat/meta/internal/credential")
public class AiModelCredentialController
        extends BaseController<AiModelCredentialDTO, AiMetaQueryRequest, AiModelCredentialService> {

    private final AiModelCredentialService service;

    public AiModelCredentialController(AiModelCredentialService service) {
        this.service = service;
    }

    @Override
    protected AiModelCredentialService service() {
        return service;
    }
}
