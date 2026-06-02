package ai.platform.aiassit.chat.meta.controller;

import ai.platform.aiassit.chat.meta.entity.dto.AiModelConfigDTO;
import ai.platform.aiassit.chat.meta.entity.req.AiMetaQueryRequest;
import ai.platform.aiassit.chat.meta.service.AiModelConfigService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat/meta/internal/model")
public class AiModelConfigController
        extends BaseController<AiModelConfigDTO, AiMetaQueryRequest, AiModelConfigService> {

    private final AiModelConfigService service;

    public AiModelConfigController(AiModelConfigService service) {
        this.service = service;
    }

    @Override
    protected AiModelConfigService service() {
        return service;
    }
}
