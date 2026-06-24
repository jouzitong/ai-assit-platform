package ai.platform.aiassit.render.core.controller;

import ai.platform.aiassit.render.api.RenderInternalApi;
import ai.platform.aiassit.render.api.dto.RenderDetailDTO;
import ai.platform.aiassit.render.api.dto.RenderGetRequest;
import ai.platform.aiassit.render.api.dto.RenderUpsertRequest;
import ai.platform.aiassit.render.core.service.RenderInternalApplicationService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Render 内部页面控制器。
 */
@RestController
@RequestMapping
public class RenderInternalController implements RenderInternalApi {

    private final RenderInternalApplicationService service;

    public RenderInternalController(RenderInternalApplicationService service) {
        this.service = service;
    }

    @Override
    public RenderDetailDTO upsert(RenderUpsertRequest request) {
        return service.upsert(request);
    }

    @Override
    public RenderDetailDTO get(RenderGetRequest request) {
        return service.get(request);
    }
}
