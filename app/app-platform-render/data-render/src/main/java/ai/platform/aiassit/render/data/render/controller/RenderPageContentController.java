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
