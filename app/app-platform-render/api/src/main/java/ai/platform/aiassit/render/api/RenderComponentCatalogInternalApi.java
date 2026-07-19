package ai.platform.aiassit.render.api;

import ai.platform.aiassit.render.api.dto.RenderComponentCatalogQueryRequest;
import ai.platform.aiassit.render.api.dto.RenderComponentCatalogResponse;
import org.athena.framework.web.vo.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Render 已发布组件目录内部接口。
 */
@FeignClient(
        name = "render",
        contextId = "platformRenderComponentCatalogInternalClient",
        path = "/render/internal/v1/render-components"
)
public interface RenderComponentCatalogInternalApi {

    /**
     * 查询当前已发布的组件目录。
     */
    @PostMapping("/catalog/query")
    R<RenderComponentCatalogResponse> queryCatalog(
            @RequestBody(required = false) RenderComponentCatalogQueryRequest request
    );
}
