package ai.platform.aiassit.render.data.render.controller;

import ai.platform.aiassit.render.api.constant.RenderBizCodeConstant;
import ai.platform.aiassit.render.data.render.entity.dto.RenderPageContentDTO;
import ai.platform.aiassit.render.data.render.entity.dto.RenderPageDTO;
import ai.platform.aiassit.render.data.render.entity.vo.RenderPageContentVO;
import ai.platform.aiassit.render.data.render.service.RenderPageContentService;
import ai.platform.aiassit.render.data.render.service.RenderPageService;
import org.arthena.framework.common.exception.BizException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

/**
 * 渲染页面内容的只读查询接口。
 *
 * <p>以页面业务编码加载已持久化的内容 JSON；页面必须存在，未设置内容时返回空对象以便运行时稳定渲染。</p>
 */
@RestController
@RequestMapping("/api/v1/renderPageContents")
public class RenderPageContentController {

    private final RenderPageService renderPageService;
    private final RenderPageContentService renderPageContentService;

    public RenderPageContentController(RenderPageService renderPageService,
                                       RenderPageContentService renderPageContentService) {
        this.renderPageService = renderPageService;
        this.renderPageContentService = renderPageContentService;
    }

    /**
     * 按页面编码获取当前渲染内容。
     *
     * @param code 页面业务编码
     * @return 页面内容视图，包含已确认的页面编码和内容 JSON
     */
    @GetMapping("/{code}")
    public RenderPageContentVO getByPageCode(@PathVariable("code") String code) {
        RenderPageDTO page = renderPageService.queryByCode(code);
        if (page == null) {
            throw BizException.of(RenderBizCodeConstant.RENDER_PAGE_NOT_FOUND, code);
        }

        RenderPageContentDTO content = renderPageContentService.queryByPageCode(code);
        RenderPageContentVO vo = new RenderPageContentVO();
        vo.setPageCode(page.getCode());
        vo.setContent(content == null ? Collections.emptyMap() : content.getContent());
        return vo;
    }
}
