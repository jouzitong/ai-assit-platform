package ai.platform.aiassit.render.api;

import ai.platform.aiassit.render.api.dto.RenderDetailDTO;
import ai.platform.aiassit.render.api.dto.RenderGetRequest;
import ai.platform.aiassit.render.api.dto.RenderUpsertRequest;
import org.athena.framework.web.annotation.IgnoredResultWrapper;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Render 内部页面接口。
 */
@FeignClient(
        name = "render",
        contextId = "platformRenderInternalClient",
        path = "/render/internal/v1/render"
)
public interface RenderInternalApi {

    /**
     * 新增或更新渲染页面。
     */
    @PostMapping("/upsert")
    @IgnoredResultWrapper
    RenderDetailDTO upsert(@RequestBody RenderUpsertRequest request);

    /**
     * 获取渲染页面详情。
     */
    @PostMapping("/get")
    @IgnoredResultWrapper
    RenderDetailDTO get(@RequestBody RenderGetRequest request);
}
