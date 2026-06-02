package ai.platform.aiassit.chat.meta.controller;

import ai.platform.aiassit.chat.meta.entity.dto.AiProviderConfigDTO;
import ai.platform.aiassit.chat.meta.entity.req.AiMetaQueryRequest;
import ai.platform.aiassit.chat.meta.service.AiProviderConfigService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat/meta/internal/provider")
public class AiProviderConfigController
        extends BaseController<AiProviderConfigDTO, AiMetaQueryRequest, AiProviderConfigService> {

    private final AiProviderConfigService service;

    public AiProviderConfigController(AiProviderConfigService service) {
        this.service = service;
    }

    @Override
    protected AiProviderConfigService service() {
        return service;
    }
}
