package ai.platform.aiassit.service.ai.kb.controller;

import ai.platform.aiassit.service.ai.kb.entity.dto.AiKbStoreDTO;
import ai.platform.aiassit.service.ai.kb.entity.req.AiKbStoreQueryRequest;
import ai.platform.aiassit.service.ai.kb.service.AiKbStoreService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/kb/internal/store")
public class AiKbStoreController extends BaseController<AiKbStoreDTO, AiKbStoreQueryRequest, AiKbStoreService> {

    private final AiKbStoreService service;

    public AiKbStoreController(AiKbStoreService service) {
        this.service = service;
    }

    @Override
    protected AiKbStoreService service() {
        return service;
    }
}
