package ai.platform.aiassit.render.data.render.service.impl;

import ai.platform.aiassit.render.api.constant.RenderBizCodeConstant;
import ai.platform.aiassit.render.api.enums.EffectiveStatus;
import ai.platform.aiassit.render.data.render.entity.dto.RenderPageCategoryDTO;
import ai.platform.aiassit.render.data.render.entity.dto.RenderPageContentDTO;
import ai.platform.aiassit.render.data.render.entity.dto.RenderPageDTO;
import ai.platform.aiassit.render.data.render.entity.dto.RenderPageSnapshotDTO;
import ai.platform.aiassit.render.data.render.entity.req.RenderPageManageQueryRequest;
import ai.platform.aiassit.render.data.render.entity.req.RenderPageManageRequest;
import ai.platform.aiassit.render.data.render.entity.req.RenderPageTreeQueryRequest;
import ai.platform.aiassit.render.data.render.entity.vo.RenderPageCategoryTreeVO;
import ai.platform.aiassit.render.data.render.entity.vo.RenderPageManageVO;
import ai.platform.aiassit.render.data.render.entity.vo.RenderPageTreeVO;
import ai.platform.aiassit.render.data.render.service.RenderPageCategoryService;
import ai.platform.aiassit.render.data.render.service.RenderPageContentService;
import ai.platform.aiassit.render.data.render.service.RenderPageManageService;
import ai.platform.aiassit.render.data.render.service.RenderPageService;
import ai.platform.aiassit.render.data.render.service.RenderPageSnapshotService;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.data.jdbc.vo.PageInfo;
import org.athena.framework.data.jdbc.vo.PageResultVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RenderPageManageServiceImpl implements RenderPageManageService {

    private static final String DEFAULT_PAGE_CONTENT = "{}";

    private final RenderPageService renderPageService;
    private final RenderPageContentService renderPageContentService;
    private final RenderPageSnapshotService renderPageSnapshotService;
    private final RenderPageCategoryService renderPageCategoryService;

    public RenderPageManageServiceImpl(RenderPageService renderPageService,
                                       RenderPageContentService renderPageContentService,
                                       RenderPageSnapshotService renderPageSnapshotService,
                                       RenderPageCategoryService renderPageCategoryService) {
        this.renderPageService = renderPageService;
        this.renderPageContentService = renderPageContentService;
        this.renderPageSnapshotService = renderPageSnapshotService;
        this.renderPageCategoryService = renderPageCategoryService;
    }

    @Override
    public PageResultVO<RenderPageManageVO> page(RenderPageManageQueryRequest request) {
        RenderPageManageQueryRequest safeQuery = request == null ? new RenderPageManageQueryRequest() : request;
        List<RenderPageManageVO> records = filterPages(loadAllPageDetails(), safeQuery);
        records.sort(Comparator.comparing(RenderPageManageVO::getUpdateTime,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RenderPageManageVO::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return toPageResult(records, safeQuery.page(), safeQuery.size());
    }

    @Override
    public RenderPageTreeVO tree(RenderPageTreeQueryRequest request) {
        RenderPageTreeQueryRequest safeQuery = request == null ? new RenderPageTreeQueryRequest() : request;
        List<RenderPageCategoryDTO> categories = renderPageCategoryService.queryAll(null);
        if (safeQuery.getEnabled() != null) {
            categories = categories.stream()
                    .filter(category -> Objects.equals(category.getEnabled(), safeQuery.getEnabled()))
                    .collect(Collectors.toList());
        }
        if (StringUtils.hasText(safeQuery.getCategoryCode())) {
            categories = filterCategoriesByRoot(categories, safeQuery.getCategoryCode());
        }

        List<RenderPageManageVO> pages = filterPages(loadAllPageDetails(), toManageQuery(safeQuery));
        if (StringUtils.hasText(safeQuery.getCategoryCode())) {
            Set<String> categoryCodes = categories.stream().map(RenderPageCategoryDTO::getCode).collect(Collectors.toSet());
            pages = pages.stream()
                    .filter(page -> StringUtils.hasText(page.getCategoryCode()) && categoryCodes.contains(page.getCategoryCode()))
                    .collect(Collectors.toList());
        }
        return buildTree(categories, pages, safeQuery.getKeyword());
    }

    @Override
    public RenderPageManageVO get(Long id) {
        RenderPageDTO page = renderPageService.get(id);
        if (page == null) {
            throw BizException.of(RenderBizCodeConstant.RENDER_PAGE_NOT_FOUND, id);
        }
        return toManageVO(page, renderPageContentService.queryByPageCode(page.getCode()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RenderPageManageVO add(RenderPageManageRequest request) {
        RenderPageDTO page = new RenderPageDTO();
        page.setCode(requirePageCode(request == null ? null : request.getCode()));
        page.setName(requirePageName(request == null ? null : request.getName()));
        page.setCategoryCode(normalizeNullable(request == null ? null : request.getCategoryCode()));
        page.setStatus(defaultStatus(request == null ? null : request.getStatus()));
        RenderPageDTO created = renderPageService.add(page);

        String content = normalizePageContent(request == null ? null : request.getContent());
        renderPageContentService.add(buildPageContent(created.getCode(), content));
        renderPageSnapshotService.add(buildSnapshot(created.getCode(), content));
        return toManageVO(created, renderPageContentService.queryByPageCode(created.getCode()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RenderPageManageVO update(Long id, RenderPageManageRequest request) {
        RenderPageDTO existing = renderPageService.get(id);
        if (existing == null) {
            throw BizException.of(RenderBizCodeConstant.RENDER_PAGE_NOT_FOUND, id);
        }

        String finalCode = StringUtils.hasText(request == null ? null : request.getCode())
                ? requirePageCode(request.getCode())
                : existing.getCode();
        RenderPageDTO payload = new RenderPageDTO();
        payload.setCode(finalCode);
        payload.setName(StringUtils.hasText(request == null ? null : request.getName()) ? requirePageName(request.getName()) : existing.getName());
        payload.setCategoryCode(request == null ? existing.getCategoryCode() : resolveCategoryCode(request.getCategoryCode(), existing.getCategoryCode()));
        payload.setStatus(request == null || request.getStatus() == null ? existing.getStatus() : request.getStatus());
        RenderPageDTO updated = renderPageService.update(id, payload);

        RenderPageContentDTO existingContent = renderPageContentService.queryByPageCode(existing.getCode());
        String finalContent = request == null || request.getContent() == null
                ? normalizePageContent(existingContent == null ? null : existingContent.getContent())
                : normalizePageContent(request.getContent());

        if (existingContent == null) {
            renderPageContentService.add(buildPageContent(finalCode, finalContent));
        } else {
            RenderPageContentDTO contentPayload = new RenderPageContentDTO();
            contentPayload.setPageCode(finalCode);
            contentPayload.setContent(finalContent);
            renderPageContentService.update(existingContent.getId(), contentPayload);
        }
        renderPageSnapshotService.add(buildSnapshot(finalCode, finalContent));
        return toManageVO(updated, renderPageContentService.queryByPageCode(finalCode));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        RenderPageDTO existing = renderPageService.get(id);
        if (existing == null) {
            throw BizException.of(RenderBizCodeConstant.RENDER_PAGE_NOT_FOUND, id);
        }
        RenderPageContentDTO content = renderPageContentService.queryByPageCode(existing.getCode());
        if (content != null) {
            renderPageContentService.delete(content.getId());
        }
        return renderPageService.delete(id);
    }

    private RenderPageManageQueryRequest toManageQuery(RenderPageTreeQueryRequest request) {
        RenderPageManageQueryRequest query = new RenderPageManageQueryRequest();
        query.setKeyword(request.getKeyword());
        query.setCategoryCode(request.getCategoryCode());
        query.setStatus(request.getStatus());
        query.setPage(request.getPage());
        query.setSize(request.getSize());
        return query;
    }

    private List<RenderPageManageVO> loadAllPageDetails() {
        List<RenderPageDTO> pages = renderPageService.queryAll(null);
        List<RenderPageContentDTO> contents = renderPageContentService.queryAll(null);
        Map<String, RenderPageContentDTO> contentMap = contents.stream()
                .collect(Collectors.toMap(RenderPageContentDTO::getPageCode, item -> item, (left, right) -> left, LinkedHashMap::new));
        return pages.stream().map(page -> toManageVO(page, contentMap.get(page.getCode()))).collect(Collectors.toList());
    }

    private List<RenderPageManageVO> filterPages(List<RenderPageManageVO> pages, RenderPageManageQueryRequest request) {
        Set<String> categoryCodes = null;
        if (StringUtils.hasText(request.getCategoryCode())) {
            categoryCodes = filterCategoriesByRoot(renderPageCategoryService.queryAll(null), request.getCategoryCode()).stream()
                    .map(RenderPageCategoryDTO::getCode)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        final Set<String> finalCategoryCodes = categoryCodes;
        return pages.stream()
                .filter(page -> request.getStatus() == null || request.getStatus() == page.getStatus())
                .filter(page -> !StringUtils.hasText(request.getKeyword()) || matchesKeyword(request.getKeyword(), page.getCode(), page.getName()))
                .filter(page -> finalCategoryCodes == null || (StringUtils.hasText(page.getCategoryCode()) && finalCategoryCodes.contains(page.getCategoryCode())))
                .collect(Collectors.toList());
    }

    private RenderPageTreeVO buildTree(List<RenderPageCategoryDTO> categories,
                                       List<RenderPageManageVO> pages,
                                       String keyword) {
        Map<String, RenderPageCategoryTreeVO> nodeMap = new LinkedHashMap<>();
        for (RenderPageCategoryDTO category : categories) {
            RenderPageCategoryTreeVO node = new RenderPageCategoryTreeVO();
            node.setId(category.getId());
            node.setCode(category.getCode());
            node.setName(category.getName());
            node.setParentCode(category.getParentCode());
            node.setPath(category.getPath());
            node.setSortNo(category.getSortNo());
            node.setEnabled(category.getEnabled());
            nodeMap.put(node.getCode(), node);
        }

        RenderPageTreeVO tree = new RenderPageTreeVO();
        for (RenderPageManageVO page : pages) {
            if (!StringUtils.hasText(page.getCategoryCode())) {
                tree.getUncategorizedPages().add(page);
                continue;
            }
            RenderPageCategoryTreeVO categoryNode = nodeMap.get(page.getCategoryCode());
            if (categoryNode != null) {
                categoryNode.getPages().add(page);
            }
        }

        for (RenderPageCategoryTreeVO node : nodeMap.values()) {
            RenderPageCategoryTreeVO parent = nodeMap.get(node.getParentCode());
            if (parent == null) {
                tree.getCategories().add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        sortTree(tree.getCategories());
        if (StringUtils.hasText(keyword)) {
            String normalized = keyword.trim().toLowerCase(Locale.ROOT);
            tree.getCategories().removeIf(node -> !pruneTree(node, normalized));
        }
        return tree;
    }

    private boolean pruneTree(RenderPageCategoryTreeVO node, String keyword) {
        node.getChildren().removeIf(child -> !pruneTree(child, keyword));
        boolean selfMatch = matchesKeyword(keyword, node.getCode(), node.getName(), node.getPath());
        boolean pageMatch = !node.getPages().isEmpty();
        boolean childMatch = !node.getChildren().isEmpty();
        return selfMatch || pageMatch || childMatch;
    }

    private void sortTree(List<RenderPageCategoryTreeVO> nodes) {
        nodes.sort(Comparator.comparing(RenderPageCategoryTreeVO::getSortNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(RenderPageCategoryTreeVO::getCode, Comparator.nullsLast(String::compareTo)));
        for (RenderPageCategoryTreeVO node : nodes) {
            node.getPages().sort(Comparator.comparing(RenderPageManageVO::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(RenderPageManageVO::getId, Comparator.nullsLast(Comparator.reverseOrder())));
            sortTree(node.getChildren());
        }
    }

    private List<RenderPageCategoryDTO> filterCategoriesByRoot(List<RenderPageCategoryDTO> categories, String categoryCode) {
        RenderPageCategoryDTO root = categories.stream().filter(item -> categoryCode.equals(item.getCode())).findFirst().orElse(null);
        if (root == null) {
            return List.of();
        }
        return categories.stream()
                .filter(item -> Objects.equals(item.getCode(), root.getCode())
                        || (StringUtils.hasText(root.getPath()) && StringUtils.hasText(item.getPath()) && item.getPath().startsWith(root.getPath())))
                .collect(Collectors.toList());
    }

    private PageResultVO<RenderPageManageVO> toPageResult(List<RenderPageManageVO> records, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 10 : size;
        int fromIndex = Math.min((safePage - 1) * safeSize, records.size());
        int toIndex = Math.min(fromIndex + safeSize, records.size());
        return PageResultVO.of(new ArrayList<>(records.subList(fromIndex, toIndex)),
                new PageInfo((long) records.size(), safeSize, safePage));
    }

    private RenderPageManageVO toManageVO(RenderPageDTO page, RenderPageContentDTO content) {
        RenderPageManageVO vo = new RenderPageManageVO();
        vo.setId(page.getId());
        vo.setCode(page.getCode());
        vo.setName(page.getName());
        vo.setCategoryCode(page.getCategoryCode());
        vo.setStatus(page.getStatus());
        vo.setContent(normalizePageContent(content == null ? null : content.getContent()));
        vo.setCreateTime(page.getCreateTime());
        vo.setUpdateTime(page.getUpdateTime());
        vo.setCreatedBy(page.getCreatedBy());
        vo.setUpdatedBy(page.getUpdatedBy());
        return vo;
    }

    private RenderPageContentDTO buildPageContent(String code, String content) {
        RenderPageContentDTO dto = new RenderPageContentDTO();
        dto.setPageCode(code);
        dto.setContent(content);
        return dto;
    }

    private RenderPageSnapshotDTO buildSnapshot(String code, String content) {
        RenderPageSnapshotDTO dto = new RenderPageSnapshotDTO();
        dto.setPageCode(code);
        dto.setContent(content);
        return dto;
    }

    private String requirePageCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw BizException.illegalParam(RenderBizCodeConstant.REQUIRED_RENDER_PAGE_CODE);
        }
        return code.trim();
    }

    private String requirePageName(String name) {
        if (!StringUtils.hasText(name)) {
            throw BizException.illegalParam(RenderBizCodeConstant.REQUIRED_RENDER_PAGE_NAME);
        }
        return name.trim();
    }

    private EffectiveStatus defaultStatus(EffectiveStatus status) {
        return status == null ? EffectiveStatus.DRAFT : status;
    }

    private String normalizePageContent(String content) {
        return StringUtils.hasText(content) ? content : DEFAULT_PAGE_CONTENT;
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String resolveCategoryCode(String incoming, String existing) {
        return incoming == null ? existing : normalizeNullable(incoming);
    }

    private boolean matchesKeyword(String keyword, String... candidates) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (candidate != null && candidate.toLowerCase(Locale.ROOT).contains(normalized)) {
                return true;
            }
        }
        return false;
    }
}
