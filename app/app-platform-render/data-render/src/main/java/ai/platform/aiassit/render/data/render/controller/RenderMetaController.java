package ai.platform.aiassit.render.data.render.controller;

import ai.platform.aiassit.render.api.constant.RenderBizCodeConstant;
import ai.platform.aiassit.render.data.render.entity.dto.RenderPageContentDTO;
import ai.platform.aiassit.render.data.render.entity.dto.RenderPageDTO;
import ai.platform.aiassit.render.data.render.entity.dto.RenderPageSnapshotDTO;
import ai.platform.aiassit.render.data.render.entity.req.RenderMetaContentUpsertRequest;
import ai.platform.aiassit.render.data.render.service.RenderPageContentService;
import ai.platform.aiassit.render.data.render.service.RenderPageService;
import ai.platform.aiassit.render.data.render.service.RenderPageSnapshotService;
import org.arthena.framework.common.exception.BizException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * 渲染页面运行元内容读写接口。
 *
 * <p>读取或保存页面的动态内容 JSON；每次保存都会在同一事务中生成内容快照，便于回溯页面运行元信息的历史版本。</p>
 */
@RestController
@RequestMapping("/api/v1/render/meta")
public class RenderMetaController {

    private static final String UPSERT_SNAPSHOT_DESCRIPTION = "render meta content upsert";

    private final RenderPageService renderPageService;
    private final RenderPageContentService renderPageContentService;
    private final RenderPageSnapshotService renderPageSnapshotService;

    public RenderMetaController(RenderPageService renderPageService,
                                RenderPageContentService renderPageContentService,
                                RenderPageSnapshotService renderPageSnapshotService) {
        this.renderPageService = renderPageService;
        this.renderPageContentService = renderPageContentService;
        this.renderPageSnapshotService = renderPageSnapshotService;
    }

    /**
     * 读取指定页面的当前运行元内容。
     *
     * @param code 页面业务编码
     * @return 标准化后的内容 JSON；页面存在但尚无内容时返回空对象
     */
    @GetMapping("/{code}")
    public Map<String, Object> getContent(@PathVariable("code") String code) {
        String pageCode = requirePageCode(code);
        requirePage(pageCode);
        RenderPageContentDTO content = renderPageContentService.queryByPageCode(pageCode);
        return normalizeContent(content == null ? null : content.getContent());
    }

    /**
     * 新增或覆盖指定页面的运行元内容，并创建内容快照。
     *
     * @param request 保存请求体，包含页面编码和完整内容 JSON
     * @return 已标准化并持久化的内容 JSON；同时已生成可追溯的快照版本
     */
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> upsertContent(@RequestBody RenderMetaContentUpsertRequest request) {
        String pageCode = requirePageCode(request == null ? null : request.getCode());
        requirePage(pageCode);
        Map<String, Object> content = normalizeContent(request == null ? null : request.getContent());

        RenderPageContentDTO existingContent = renderPageContentService.queryByPageCode(pageCode);
        if (existingContent == null) {
            RenderPageContentDTO payload = new RenderPageContentDTO();
            payload.setPageCode(pageCode);
            payload.setContent(content);
            renderPageContentService.add(payload);
        } else {
            RenderPageContentDTO payload = new RenderPageContentDTO();
            payload.setPageCode(pageCode);
            payload.setContent(content);
            renderPageContentService.update(existingContent.getId(), payload);
        }

        renderPageSnapshotService.add(buildSnapshot(pageCode, content));
        return content;
    }

    private RenderPageDTO requirePage(String code) {
        RenderPageDTO page = renderPageService.queryByCode(code);
        if (page == null) {
            throw BizException.of(RenderBizCodeConstant.RENDER_PAGE_NOT_FOUND, code);
        }
        return page;
    }

    private String requirePageCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw BizException.illegalParam(RenderBizCodeConstant.REQUIRED_RENDER_PAGE_CODE);
        }
        return code.trim();
    }

    private Map<String, Object> normalizeContent(Map<String, Object> content) {
        return content == null ? Collections.emptyMap() : content;
    }

    private RenderPageSnapshotDTO buildSnapshot(String pageCode, Map<String, Object> content) {
        RenderPageSnapshotDTO snapshot = new RenderPageSnapshotDTO();
        snapshot.setPageCode(pageCode);
        snapshot.setSnapshotVersion(renderPageSnapshotService.nextSnapshotVersion(pageCode));
        snapshot.setDescription(UPSERT_SNAPSHOT_DESCRIPTION);
        snapshot.setContent(content);
        return snapshot;
    }
}
