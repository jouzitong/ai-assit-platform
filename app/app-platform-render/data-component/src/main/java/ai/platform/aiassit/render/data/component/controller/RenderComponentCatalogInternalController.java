package ai.platform.aiassit.render.data.component.controller;

import ai.platform.aiassit.render.api.RenderComponentCatalogInternalApi;
import ai.platform.aiassit.render.api.dto.RenderComponentCatalogQueryRequest;
import ai.platform.aiassit.render.api.dto.RenderComponentCatalogResponse;
import ai.platform.aiassit.render.data.component.service.RenderComponentCatalogApplicationService;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/render-components")
public class RenderComponentCatalogInternalController implements RenderComponentCatalogInternalApi {

    private final RenderComponentCatalogApplicationService applicationService;

    public RenderComponentCatalogInternalController(RenderComponentCatalogApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    @PostMapping("/catalog/query")
    public R<RenderComponentCatalogResponse> queryCatalog(
            @RequestBody(required = false) RenderComponentCatalogQueryRequest request) {
        return R.ok(applicationService.query(request));
    }
}
