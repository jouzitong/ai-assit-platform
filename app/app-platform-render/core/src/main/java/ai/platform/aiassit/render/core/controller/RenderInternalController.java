package ai.platform.aiassit.render.core.controller;

import ai.platform.aiassit.render.api.RenderInternalApi;
import ai.platform.aiassit.render.api.dto.RenderDetailDTO;
import ai.platform.aiassit.render.api.dto.RenderGetRequest;
import ai.platform.aiassit.render.api.dto.RenderUpsertRequest;
import ai.platform.aiassit.render.core.service.RenderInternalApplicationService;
import org.athena.framework.web.annotation.IgnoredResultWrapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Render 内部页面控制器。
 */
@RestController
@RequestMapping("/internal/v1/render")
public class RenderInternalController implements RenderInternalApi {

    private final RenderInternalApplicationService service;

    public RenderInternalController(RenderInternalApplicationService service) {
        this.service = service;
    }

    @Override
    @IgnoredResultWrapper
    @PostMapping("/upsert")
    public RenderDetailDTO upsert(@RequestBody RenderUpsertRequest request) {
        return service.upsert(request);
    }

    @Override
    @IgnoredResultWrapper
    @PostMapping("/get")
    public RenderDetailDTO get(@RequestBody RenderGetRequest request) {
        return service.get(request);
    }
}
