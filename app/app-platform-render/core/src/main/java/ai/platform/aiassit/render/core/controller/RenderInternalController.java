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
 * Render 页面定义的内部读写接口。
 *
 * <p>供平台内服务按稳定业务标识保存或获取渲染页面的完整定义，结果采用未包装契约，便于调用方直接消费页面内容。</p>
 */
@RestController
@RequestMapping("/internal/v1/render")
public class RenderInternalController implements RenderInternalApi {

    private final RenderInternalApplicationService service;

    public RenderInternalController(RenderInternalApplicationService service) {
        this.service = service;
    }

    /**
     * 新增或更新一个渲染页面定义。
     *
     * @param request 页面保存请求体，包含页面标识、元信息、布局和组件内容
     * @return 未包装的页面详情，包含持久化后的页面定义
     */
    @Override
    @IgnoredResultWrapper
    @PostMapping("/upsert")
    public RenderDetailDTO upsert(@RequestBody RenderUpsertRequest request) {
        return service.upsert(request);
    }

    /**
     * 按页面定位条件获取渲染页面定义。
     *
     * @param request 页面查询请求体，包含页面编码或其他稳定定位信息
     * @return 未包装的页面详情；不存在时由应用服务按契约处理
     */
    @Override
    @IgnoredResultWrapper
    @PostMapping("/get")
    public RenderDetailDTO get(@RequestBody RenderGetRequest request) {
        return service.get(request);
    }
}
