package ai.platform.aiassit.render.core.service;

import ai.platform.aiassit.render.api.constant.RenderBizCodeConstant;
import ai.platform.aiassit.render.api.dto.RenderDetailDTO;
import ai.platform.aiassit.render.api.dto.RenderGetRequest;
import ai.platform.aiassit.render.api.dto.RenderUpsertRequest;
import ai.platform.aiassit.render.api.enums.EffectiveStatus;
import ai.platform.aiassit.render.data.render.entity.dto.RenderPageContentDTO;
import ai.platform.aiassit.render.data.render.entity.dto.RenderPageDTO;
import ai.platform.aiassit.render.data.render.entity.dto.RenderPageSnapshotDTO;
import ai.platform.aiassit.render.data.render.service.RenderPageContentService;
import ai.platform.aiassit.render.data.render.service.RenderPageService;
import ai.platform.aiassit.render.data.render.service.RenderPageSnapshotService;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Render 内部页面应用服务。
 */
@Service
@Slf4j
public class RenderInternalApplicationService {

    private final RenderPageService renderPageService;
    private final RenderPageContentService renderPageContentService;
    private final RenderPageSnapshotService renderPageSnapshotService;

    public RenderInternalApplicationService(
            RenderPageService renderPageService,
            RenderPageContentService renderPageContentService,
            RenderPageSnapshotService renderPageSnapshotService
    ) {
        this.renderPageService = renderPageService;
        this.renderPageContentService = renderPageContentService;
        this.renderPageSnapshotService = renderPageSnapshotService;
    }

    @Transactional(rollbackFor = Exception.class)
    public RenderDetailDTO upsert(RenderUpsertRequest request) {
        log.info("开始保存渲染页面, request: {}", request);
        String code = requireCode(request.getCode());
        RenderPageDTO existingPage = renderPageService.queryByCode(code);
        log.debug("渲染页面查询完成，code={}, exists={}", code, existingPage != null);
        if (existingPage == null) {
            log.info("渲染页面不存在，准备创建新页面，code={}", code);
            return createNew(request, code);
        }
        log.info("渲染页面已存在，准备更新页面，code={}, pageId={}", code, existingPage.getId());
        return updateExisting(request, existingPage);
    }

    private RenderDetailDTO createNew(RenderUpsertRequest request, String code) {
        RenderPageDTO page = new RenderPageDTO();
        page.setCode(code);
        page.setName(requireName(request.getName()));
        page.setCategoryCode(normalizeNullable(request.getCategoryCode()));
        page.setStatus(defaultStatus(request.getStatus()));
        RenderPageDTO createdPage = renderPageService.add(page);
        log.info("渲染页面基础信息创建完成，code={}, pageId={}", code, createdPage.getId());

        String content = normalizeContent(request.getContent());
        renderPageContentService.add(buildContentDto(code, content));
        renderPageSnapshotService.add(buildSnapshotDto(code, content));
        log.info("渲染页面内容创建完成，code={}, contentLength={}", code, content.length());
        return toDetail(createdPage, content);
    }

    private RenderDetailDTO updateExisting(RenderUpsertRequest request, RenderPageDTO existingPage) {
        String code = existingPage.getCode();
        RenderPageDTO updatePage = new RenderPageDTO();
        updatePage.setCode(existingPage.getCode());
        updatePage.setName(StringUtils.hasText(request.getName()) ? request.getName().trim() : existingPage.getName());
        updatePage.setCategoryCode(resolveCategoryCode(request.getCategoryCode(), existingPage.getCategoryCode()));
        updatePage.setStatus(request.getStatus() == null ? existingPage.getStatus() : request.getStatus());
        RenderPageDTO updatedPage = renderPageService.update(existingPage.getId(), updatePage);
        log.info("渲染页面基础信息更新完成，code={}, pageId={}", code, existingPage.getId());

        RenderPageContentDTO existingContent = renderPageContentService.queryByPageCode(code);
        log.debug("渲染页面内容查询完成，code={}, exists={}", code, existingContent != null);
        String finalContent = resolveContent(request.getContent(), existingContent == null ? null : existingContent.getContent());
        if (existingContent == null) {
            renderPageContentService.add(buildContentDto(code, finalContent));
            log.info("渲染页面内容不存在，已创建内容记录，code={}, contentLength={}", code, finalContent.length());
        } else {
            RenderPageContentDTO updateContent = new RenderPageContentDTO();
            updateContent.setPageCode(code);
            updateContent.setContent(finalContent);
            renderPageContentService.update(existingContent.getId(), updateContent);
            log.info("渲染页面内容更新完成，code={}, contentId={}, contentLength={}", code, existingContent.getId(), finalContent.length());
        }

        renderPageSnapshotService.add(buildSnapshotDto(code, finalContent));
        log.info("渲染页面快照创建完成，code={}, contentLength={}", code, finalContent.length());
        return toDetail(updatedPage, finalContent);
    }

    public RenderDetailDTO get(RenderGetRequest request) {
        String code = requireCode(request == null ? null : request.getCode());
        log.debug("开始查询渲染页面详情，code={}", code);
        RenderPageDTO page = requirePage(code);
        RenderPageContentDTO content = renderPageContentService.queryByPageCode(code);
        String contentValue = content == null ? null : content.getContent();
        log.debug("渲染页面详情查询完成，code={}, pageId={}, hasContent={}, contentLength={}",
                code, page.getId(), contentValue != null, contentValue == null ? 0 : contentValue.length());
        return toDetail(page, contentValue);
    }

    private RenderPageDTO requirePage(String code) {
        RenderPageDTO page = renderPageService.queryByCode(code);
        if (page == null) {
            log.warn("渲染页面不存在，code={}", code);
            throw BizException.of(RenderBizCodeConstant.RENDER_PAGE_NOT_FOUND, code);
        }
        return page;
    }

    private RenderPageContentDTO buildContentDto(String code, String content) {
        RenderPageContentDTO dto = new RenderPageContentDTO();
        dto.setPageCode(code);
        dto.setContent(content);
        return dto;
    }

    private RenderPageSnapshotDTO buildSnapshotDto(String code, String content) {
        RenderPageSnapshotDTO dto = new RenderPageSnapshotDTO();
        dto.setPageCode(code);
        dto.setContent(content);
        return dto;
    }

    private RenderDetailDTO toDetail(RenderPageDTO page, String content) {
        RenderDetailDTO dto = new RenderDetailDTO();
        dto.setCode(page.getCode());
        dto.setName(page.getName());
        dto.setCategoryCode(page.getCategoryCode());
        dto.setStatus(page.getStatus());
        dto.setContent(content);
        return dto;
    }

    private EffectiveStatus defaultStatus(EffectiveStatus status) {
        return status == null ? EffectiveStatus.DRAFT : status;
    }

    private String requireCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw BizException.illegalParam(RenderBizCodeConstant.REQUIRED_RENDER_PAGE_CODE);
        }
        return code.trim();
    }

    private String requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw BizException.illegalParam(RenderBizCodeConstant.REQUIRED_RENDER_PAGE_NAME);
        }
        return name.trim();
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeContent(String content) {
        return StringUtils.hasText(content) ? content : "{}";
    }

    private String resolveContent(String incoming, String existing) {
        if (incoming != null) {
            return normalizeContent(incoming);
        }
        return existing == null ? "{}" : existing;
    }

    private String resolveCategoryCode(String incoming, String existing) {
        return incoming == null ? existing : normalizeNullable(incoming);
    }
}
