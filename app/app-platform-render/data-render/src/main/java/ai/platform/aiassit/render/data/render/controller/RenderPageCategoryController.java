package ai.platform.aiassit.render.data.render.controller;

import ai.platform.aiassit.render.data.render.entity.dto.RenderPageCategoryDTO;
import ai.platform.aiassit.render.data.render.entity.req.RenderPageCategoryQueryRequest;
import ai.platform.aiassit.render.data.render.entity.vo.RenderPageCategoryTreeVO;
import ai.platform.aiassit.render.data.render.service.RenderPageCategoryService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/render/page-categories")
public class RenderPageCategoryController
        extends BaseController<RenderPageCategoryDTO, RenderPageCategoryQueryRequest, RenderPageCategoryService> {

    private final RenderPageCategoryService service;

    public RenderPageCategoryController(RenderPageCategoryService service) {
        this.service = service;
    }

    @PostMapping("/tree")
    public List<RenderPageCategoryTreeVO> tree(@RequestBody(required = false) RenderPageCategoryQueryRequest request) {
        return service.queryTree(request);
    }

    @Override
    protected RenderPageCategoryService service() {
        return service;
    }
}
