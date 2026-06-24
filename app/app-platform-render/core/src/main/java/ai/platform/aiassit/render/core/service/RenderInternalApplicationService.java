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
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Render 内部页面应用服务。
 */
@Service
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
        String code = requireCode(request.getCode());
        RenderPageDTO existingPage = renderPageService.queryByCode(code);
        if (existingPage == null) {
            return createNew(request, code);
        }
        return updateExisting(request, existingPage);
    }

    private RenderDetailDTO createNew(RenderUpsertRequest request, String code) {
        RenderPageDTO page = new RenderPageDTO();
        page.setCode(code);
        page.setName(requireName(request.getName()));
        page.setCategoryCode(normalizeNullable(request.getCategoryCode()));
        page.setStatus(defaultStatus(request.getStatus()));
        RenderPageDTO createdPage = renderPageService.add(page);

        String content = normalizeContent(request.getContent());
        renderPageContentService.add(buildContentDto(code, content));
        renderPageSnapshotService.add(buildSnapshotDto(code, content));
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

        RenderPageContentDTO existingContent = renderPageContentService.queryByPageCode(code);
        String finalContent = resolveContent(request.getContent(), existingContent == null ? null : existingContent.getContent());
        if (existingContent == null) {
            renderPageContentService.add(buildContentDto(code, finalContent));
        } else {
            RenderPageContentDTO updateContent = new RenderPageContentDTO();
            updateContent.setPageCode(code);
            updateContent.setContent(finalContent);
            renderPageContentService.update(existingContent.getId(), updateContent);
        }

        renderPageSnapshotService.add(buildSnapshotDto(code, finalContent));
        return toDetail(updatedPage, finalContent);
    }

    public RenderDetailDTO get(RenderGetRequest request) {
        String code = requireCode(request == null ? null : request.getCode());
        RenderPageDTO page = requirePage(code);
        RenderPageContentDTO content = renderPageContentService.queryByPageCode(code);
        return toDetail(page, content == null ? null : content.getContent());
    }

    private RenderPageDTO requirePage(String code) {
        RenderPageDTO page = renderPageService.queryByCode(code);
        if (page == null) {
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
