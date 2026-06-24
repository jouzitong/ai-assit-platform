package ai.platform.aiassit.render.data.component.service.impl;

import ai.platform.aiassit.render.api.constant.RenderBizCodeConstant;
import ai.platform.aiassit.render.api.enums.EffectiveStatus;
import ai.platform.aiassit.render.data.component.entity.dto.RenderComponentContentDTO;
import ai.platform.aiassit.render.data.component.entity.dto.RenderComponentDTO;
import ai.platform.aiassit.render.data.component.entity.dto.RenderComponentSnapshotDTO;
import ai.platform.aiassit.render.data.component.entity.req.RenderComponentManageQueryRequest;
import ai.platform.aiassit.render.data.component.entity.req.RenderComponentManageRequest;
import ai.platform.aiassit.render.data.component.entity.vo.RenderComponentManageVO;
import ai.platform.aiassit.render.data.component.service.RenderComponentContentService;
import ai.platform.aiassit.render.data.component.service.RenderComponentManageService;
import ai.platform.aiassit.render.data.component.service.RenderComponentService;
import ai.platform.aiassit.render.data.component.service.RenderComponentSnapshotService;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.data.jdbc.vo.PageInfo;
import org.athena.framework.data.jdbc.vo.PageResultVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RenderComponentManageServiceImpl implements RenderComponentManageService {

    private static final String DEFAULT_EXAMPLE_JSON = "{}";

    private final RenderComponentService renderComponentService;
    private final RenderComponentContentService renderComponentContentService;
    private final RenderComponentSnapshotService renderComponentSnapshotService;

    public RenderComponentManageServiceImpl(RenderComponentService renderComponentService,
                                            RenderComponentContentService renderComponentContentService,
                                            RenderComponentSnapshotService renderComponentSnapshotService) {
        this.renderComponentService = renderComponentService;
        this.renderComponentContentService = renderComponentContentService;
        this.renderComponentSnapshotService = renderComponentSnapshotService;
    }

    @Override
    public PageResultVO<RenderComponentManageVO> page(RenderComponentManageQueryRequest request) {
        RenderComponentManageQueryRequest safeQuery = request == null ? new RenderComponentManageQueryRequest() : request;
        List<RenderComponentManageVO> records = loadAllComponents().stream()
                .filter(component -> safeQuery.getStatus() == null || safeQuery.getStatus() == component.getStatus())
                .filter(component -> !StringUtils.hasText(safeQuery.getCategory()) || safeQuery.getCategory().equals(component.getCategory()))
                .filter(component -> !StringUtils.hasText(safeQuery.getKeyword())
                        || matchesKeyword(safeQuery.getKeyword(), component.getKey(), component.getName(), component.getCategory()))
                .sorted(Comparator.comparing(RenderComponentManageVO::getUpdateTime,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RenderComponentManageVO::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return toPageResult(records, safeQuery.page(), safeQuery.size());
    }

    @Override
    public RenderComponentManageVO get(Long id) {
        RenderComponentDTO component = renderComponentService.get(id);
        if (component == null) {
            throw BizException.of(RenderBizCodeConstant.COMPONENT_NOT_FOUND, id);
        }
        return toManageVO(component, renderComponentContentService.queryByComponentKey(component.getKey()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RenderComponentManageVO add(RenderComponentManageRequest request) {
        RenderComponentDTO dto = new RenderComponentDTO();
        dto.setKey(requireComponentKey(request == null ? null : request.getKey()));
        dto.setName(requireComponentName(request == null ? null : request.getName()));
        dto.setCategory(normalizeNullable(request == null ? null : request.getCategory()));
        dto.setStatus(defaultStatus(request == null ? null : request.getStatus()));
        RenderComponentDTO created = renderComponentService.add(dto);

        String docMarkdown = normalizeDocMarkdown(request == null ? null : request.getDocMarkdown());
        String exampleJson = normalizeExampleJson(request == null ? null : request.getExampleJson());
        renderComponentContentService.add(buildContent(created.getKey(), docMarkdown, exampleJson));
        renderComponentSnapshotService.add(buildSnapshot(created.getKey(), docMarkdown, exampleJson));
        return toManageVO(created, renderComponentContentService.queryByComponentKey(created.getKey()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RenderComponentManageVO update(Long id, RenderComponentManageRequest request) {
        RenderComponentDTO existing = renderComponentService.get(id);
        if (existing == null) {
            throw BizException.of(RenderBizCodeConstant.COMPONENT_NOT_FOUND, id);
        }

        String finalKey = StringUtils.hasText(request == null ? null : request.getKey())
                ? requireComponentKey(request.getKey())
                : existing.getKey();
        RenderComponentDTO payload = new RenderComponentDTO();
        payload.setKey(finalKey);
        payload.setName(StringUtils.hasText(request == null ? null : request.getName()) ? requireComponentName(request.getName()) : existing.getName());
        payload.setCategory(request == null ? existing.getCategory() : resolveCategory(request.getCategory(), existing.getCategory()));
        payload.setStatus(request == null || request.getStatus() == null ? existing.getStatus() : request.getStatus());
        RenderComponentDTO updated = renderComponentService.update(id, payload);

        RenderComponentContentDTO existingContent = renderComponentContentService.queryByComponentKey(existing.getKey());
        String finalDocMarkdown = request == null || request.getDocMarkdown() == null
                ? normalizeDocMarkdown(existingContent == null ? null : existingContent.getDocMarkdown())
                : normalizeDocMarkdown(request.getDocMarkdown());
        String finalExampleJson = request == null || request.getExampleJson() == null
                ? normalizeExampleJson(existingContent == null ? null : existingContent.getExampleJson())
                : normalizeExampleJson(request.getExampleJson());

        if (existingContent == null) {
            renderComponentContentService.add(buildContent(finalKey, finalDocMarkdown, finalExampleJson));
        } else {
            RenderComponentContentDTO contentPayload = new RenderComponentContentDTO();
            contentPayload.setComponentKey(finalKey);
            contentPayload.setDocMarkdown(finalDocMarkdown);
            contentPayload.setExampleJson(finalExampleJson);
            renderComponentContentService.update(existingContent.getId(), contentPayload);
        }

        renderComponentSnapshotService.add(buildSnapshot(finalKey, finalDocMarkdown, finalExampleJson));
        return toManageVO(updated, renderComponentContentService.queryByComponentKey(finalKey));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        RenderComponentDTO existing = renderComponentService.get(id);
        if (existing == null) {
            throw BizException.of(RenderBizCodeConstant.COMPONENT_NOT_FOUND, id);
        }
        RenderComponentContentDTO content = renderComponentContentService.queryByComponentKey(existing.getKey());
        if (content != null) {
            renderComponentContentService.delete(content.getId());
        }
        return renderComponentService.delete(id);
    }

    private List<RenderComponentManageVO> loadAllComponents() {
        List<RenderComponentDTO> components = renderComponentService.queryAll(null);
        List<RenderComponentContentDTO> contents = renderComponentContentService.queryAll(null);
        Map<String, RenderComponentContentDTO> contentMap = contents.stream()
                .collect(Collectors.toMap(RenderComponentContentDTO::getComponentKey, item -> item, (left, right) -> left, LinkedHashMap::new));
        return components.stream().map(component -> toManageVO(component, contentMap.get(component.getKey()))).collect(Collectors.toList());
    }

    private PageResultVO<RenderComponentManageVO> toPageResult(List<RenderComponentManageVO> records, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 10 : size;
        int fromIndex = Math.min((safePage - 1) * safeSize, records.size());
        int toIndex = Math.min(fromIndex + safeSize, records.size());
        return PageResultVO.of(new ArrayList<>(records.subList(fromIndex, toIndex)),
                new PageInfo((long) records.size(), safeSize, safePage));
    }

    private RenderComponentManageVO toManageVO(RenderComponentDTO component, RenderComponentContentDTO content) {
        RenderComponentManageVO vo = new RenderComponentManageVO();
        vo.setId(component.getId());
        vo.setKey(component.getKey());
        vo.setName(component.getName());
        vo.setCategory(component.getCategory());
        vo.setStatus(component.getStatus());
        vo.setDocMarkdown(normalizeDocMarkdown(content == null ? null : content.getDocMarkdown()));
        vo.setExampleJson(normalizeExampleJson(content == null ? null : content.getExampleJson()));
        vo.setCreateTime(component.getCreateTime());
        vo.setUpdateTime(component.getUpdateTime());
        vo.setCreatedBy(component.getCreatedBy());
        vo.setUpdatedBy(component.getUpdatedBy());
        return vo;
    }

    private RenderComponentContentDTO buildContent(String key, String docMarkdown, String exampleJson) {
        RenderComponentContentDTO dto = new RenderComponentContentDTO();
        dto.setComponentKey(key);
        dto.setDocMarkdown(docMarkdown);
        dto.setExampleJson(exampleJson);
        return dto;
    }

    private RenderComponentSnapshotDTO buildSnapshot(String key, String docMarkdown, String exampleJson) {
        RenderComponentSnapshotDTO dto = new RenderComponentSnapshotDTO();
        dto.setComponentKey(key);
        dto.setDocMarkdown(docMarkdown);
        dto.setExampleJson(exampleJson);
        return dto;
    }

    private String requireComponentKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw BizException.illegalParam(RenderBizCodeConstant.REQUIRED_COMPONENT_KEY);
        }
        return key.trim();
    }

    private String requireComponentName(String name) {
        if (!StringUtils.hasText(name)) {
            throw BizException.illegalParam(RenderBizCodeConstant.REQUIRED_COMPONENT_NAME);
        }
        return name.trim();
    }

    private EffectiveStatus defaultStatus(EffectiveStatus status) {
        return status == null ? EffectiveStatus.DRAFT : status;
    }

    private String normalizeDocMarkdown(String docMarkdown) {
        return docMarkdown == null ? "" : docMarkdown;
    }

    private String normalizeExampleJson(String exampleJson) {
        return StringUtils.hasText(exampleJson) ? exampleJson : DEFAULT_EXAMPLE_JSON;
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String resolveCategory(String incoming, String existing) {
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
