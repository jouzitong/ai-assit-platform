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

/**
 * 已发布渲染组件目录的内部查询接口。
 *
 * <p>仅返回可在运行时安全使用的组件目录与版本信息，供页面渲染、AI 生成和其他内部服务选择组件。</p>
 */
@RestController
@RequestMapping("/internal/v1/render-components")
public class RenderComponentCatalogInternalController implements RenderComponentCatalogInternalApi {

    private final RenderComponentCatalogApplicationService applicationService;

    public RenderComponentCatalogInternalController(RenderComponentCatalogApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 按可选条件查询当前已发布的组件目录。
     *
     * @param request 可选查询请求体，包含组件类别、关键字或运行时兼容条件
     * @return 包装后的组件目录，包含可用组件、版本和属性摘要
     */
    @Override
    @PostMapping("/catalog/query")
    public R<RenderComponentCatalogResponse> queryCatalog(
            @RequestBody(required = false) RenderComponentCatalogQueryRequest request) {
        return R.ok(applicationService.query(request));
    }
}
