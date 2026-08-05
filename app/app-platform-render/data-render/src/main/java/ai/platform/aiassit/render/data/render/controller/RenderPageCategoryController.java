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

/**
 * 渲染页面分类的通用 CRUD 与树形目录接口。
 *
 * <p>复用 {@link BaseController} 维护分类基础信息；请求体使用 {@link RenderPageCategoryDTO}，查询条件使用
 * {@link RenderPageCategoryQueryRequest}，额外提供面向导航的层级树查询。</p>
 */
@RestController
@RequestMapping("/api/v1/render/page-categories")
public class RenderPageCategoryController
        extends BaseController<RenderPageCategoryDTO, RenderPageCategoryQueryRequest, RenderPageCategoryService> {

    private final RenderPageCategoryService service;

    public RenderPageCategoryController(RenderPageCategoryService service) {
        this.service = service;
    }

    /**
     * 按可选条件查询渲染页面分类树。
     *
     * @param request 可选分类查询请求体，包含根节点、状态或关键字等过滤条件
     * @return 层级化分类树，供页面导航与页面归类使用
     */
    @PostMapping("/tree")
    public List<RenderPageCategoryTreeVO> tree(@RequestBody(required = false) RenderPageCategoryQueryRequest request) {
        return service.queryTree(request);
    }

    @Override
    protected RenderPageCategoryService service() {
        return service;
    }
}
