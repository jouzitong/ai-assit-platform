package ai.platform.aiassit.render.data.component.service.impl;

import ai.platform.aiassit.render.api.dto.RenderComponentCatalogComponentDTO;
import ai.platform.aiassit.render.api.dto.RenderComponentCatalogQueryRequest;
import ai.platform.aiassit.render.api.dto.RenderComponentCatalogResponse;
import ai.platform.aiassit.render.api.enums.EffectiveStatus;
import ai.platform.aiassit.render.data.component.entity.dto.RenderComponentContentDTO;
import ai.platform.aiassit.render.data.component.entity.dto.RenderComponentDTO;
import ai.platform.aiassit.render.data.component.service.RenderComponentCatalogApplicationService;
import ai.platform.aiassit.render.data.component.service.RenderComponentContentService;
import ai.platform.aiassit.render.data.component.service.RenderComponentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RenderComponentCatalogApplicationServiceImpl implements RenderComponentCatalogApplicationService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 100;
    private static final String COMPONENT_ASSET_SCHEMA = "component-asset/v1";
    private static final String SOURCE_REVISION_SCHEMA = "render-component-source/v1";
    private static final String CATALOG_REVISION_SCHEMA = "render-component-catalog/v1";

    private final RenderComponentService renderComponentService;
    private final RenderComponentContentService renderComponentContentService;
    private final ObjectMapper objectMapper;

    public RenderComponentCatalogApplicationServiceImpl(RenderComponentService renderComponentService,
                                                        RenderComponentContentService renderComponentContentService,
                                                        ObjectMapper objectMapper) {
        this.renderComponentService = renderComponentService;
        this.renderComponentContentService = renderComponentContentService;
        this.objectMapper = objectMapper;
    }

    @Override
    public RenderComponentCatalogResponse query(RenderComponentCatalogQueryRequest request) {
        List<RenderComponentCatalogComponentDTO> publishedCatalog = loadPublishedCatalog();

        RenderComponentCatalogResponse response = new RenderComponentCatalogResponse();
        response.setCatalogRevision(calculateCatalogRevision(publishedCatalog));
        response.setComponents(publishedCatalog.stream()
                .filter(component -> matches(request, component))
                .limit(resolveLimit(request))
                .toList());
        return response;
    }

    private List<RenderComponentCatalogComponentDTO> loadPublishedCatalog() {
        Map<String, RenderComponentContentDTO> contentByComponentKey = loadLatestContentByComponentKey();
        List<RenderComponentDTO> components = nullSafe(renderComponentService.queryAll(null));
        return components.stream()
                .filter(component -> component != null && EffectiveStatus.PUBLISHED == component.getStatus())
                .filter(component -> StringUtils.hasText(component.getKey()))
                .map(component -> toCatalogComponent(component, contentByComponentKey.get(component.getKey())))
                .sorted(Comparator.comparing(RenderComponentCatalogComponentDTO::getComponentKey))
                .toList();
    }

    private Map<String, RenderComponentContentDTO> loadLatestContentByComponentKey() {
        Map<String, RenderComponentContentDTO> contents = new HashMap<>();
        for (RenderComponentContentDTO candidate : nullSafe(renderComponentContentService.queryAll(null))) {
            if (candidate == null || !StringUtils.hasText(candidate.getComponentKey())) {
                continue;
            }
            contents.merge(candidate.getComponentKey(), candidate, this::newerContent);
        }
        return contents;
    }

    private RenderComponentContentDTO newerContent(RenderComponentContentDTO left,
                                                    RenderComponentContentDTO right) {
        Comparator<RenderComponentContentDTO> comparator = Comparator
                .comparing(RenderComponentContentDTO::getUpdateTime,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(RenderComponentContentDTO::getId,
                        Comparator.nullsFirst(Comparator.naturalOrder()));
        return comparator.compare(left, right) >= 0 ? left : right;
    }

    private RenderComponentCatalogComponentDTO toCatalogComponent(RenderComponentDTO component,
                                                                   RenderComponentContentDTO content) {
        String docMarkdown = content == null ? null : content.getDocMarkdown();
        String exampleJson = content == null ? null : content.getExampleJson();
        String componentVersion = extractComponentVersion(exampleJson);

        RenderComponentCatalogComponentDTO result = new RenderComponentCatalogComponentDTO();
        result.setComponentKey(component.getKey());
        result.setName(component.getName());
        result.setCategory(component.getCategory());
        result.setComponentVersion(componentVersion);
        result.setDocMarkdown(docMarkdown);
        result.setExampleJson(exampleJson);
        result.setUpdatedAt(latest(component.getUpdateTime(), content == null ? null : content.getUpdateTime()));
        result.setSourceRevision(sha256(Arrays.asList(
                SOURCE_REVISION_SCHEMA,
                component.getKey(),
                component.getName(),
                component.getCategory(),
                Integer.toString(component.getStatus().getCode()),
                componentVersion,
                docMarkdown,
                exampleJson
        )));
        return result;
    }

    private String extractComponentVersion(String exampleJson) {
        if (!StringUtils.hasText(exampleJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(exampleJson);
            if (root == null || !root.isObject()
                    || !COMPONENT_ASSET_SCHEMA.equals(root.path("schemaVersion").asText())) {
                return null;
            }
            JsonNode version = root.path("sourceComponent").path("version");
            return version.isTextual() && StringUtils.hasText(version.textValue())
                    ? version.textValue().trim()
                    : null;
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private boolean matches(RenderComponentCatalogQueryRequest request,
                            RenderComponentCatalogComponentDTO component) {
        if (request == null) {
            return true;
        }
        Set<String> componentKeys = normalizeComponentKeys(request.getComponentKeys());
        if (!componentKeys.isEmpty() && !componentKeys.contains(component.getComponentKey())) {
            return false;
        }
        if (StringUtils.hasText(request.getCategory())
                && !request.getCategory().trim().equalsIgnoreCase(component.getCategory())) {
            return false;
        }
        if (!StringUtils.hasText(request.getKeyword())) {
            return true;
        }
        String keyword = request.getKeyword().trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(component.getComponentKey(), keyword)
                || containsIgnoreCase(component.getName(), keyword)
                || containsIgnoreCase(component.getCategory(), keyword)
                || containsIgnoreCase(component.getDocMarkdown(), keyword);
    }

    private Set<String> normalizeComponentKeys(List<String> keys) {
        Set<String> normalized = new LinkedHashSet<>();
        if (keys == null) {
            return normalized;
        }
        for (String key : keys) {
            if (StringUtils.hasText(key)) {
                normalized.add(key.trim());
            }
        }
        return normalized;
    }

    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private long resolveLimit(RenderComponentCatalogQueryRequest request) {
        Integer requested = request == null ? null : request.getLimit();
        if (requested == null || requested < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    private String calculateCatalogRevision(List<RenderComponentCatalogComponentDTO> components) {
        List<String> revisionParts = new ArrayList<>();
        revisionParts.add(CATALOG_REVISION_SCHEMA);
        for (RenderComponentCatalogComponentDTO component : components) {
            revisionParts.add(component.getComponentKey());
            revisionParts.add(component.getSourceRevision());
        }
        return sha256(revisionParts);
    }

    private String sha256(List<String> parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                if (part == null) {
                    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
                    continue;
                }
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private LocalDateTime latest(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
