package ai.platform.aiassit.model.controller;

import ai.platform.aiassit.model.entity.dto.AiModelConfigDTO;
import ai.platform.aiassit.model.entity.req.AiMetaQueryRequest;
import ai.platform.aiassit.model.service.AiModelConfigService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/meta/internal/model")
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
