package ai.platform.aiassit.chat.agent.control.data.service.control.impl;

import ai.platform.aiassit.chat.agent.control.data.entity.AiChatToolVersionEntity;
import ai.platform.aiassit.chat.agent.control.data.enums.DefinitionStatus;
import ai.platform.aiassit.chat.agent.control.data.mapper.AiChatToolVersionMapper;
import ai.platform.aiassit.chat.agent.control.data.support.ControlPlaneJsonSupport;
import ai.platform.aiassit.service.ai.spi.tool.PublishedToolDefinition;
import ai.platform.aiassit.service.ai.spi.tool.PublishedToolDefinitionStore;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Database-backed immutable Tool lookup used by the Tool Gateway. */
@Component
public class PublishedToolDefinitionStoreImpl implements PublishedToolDefinitionStore {

    private final AiChatToolVersionMapper versionMapper;
    private final ControlPlaneJsonSupport json;

    public PublishedToolDefinitionStoreImpl(AiChatToolVersionMapper versionMapper,
                                            ControlPlaneJsonSupport json) {
        this.versionMapper = versionMapper;
        this.json = json;
    }

    @Override
    public Optional<PublishedToolDefinition> findPublished(String toolCode, Integer toolVersion) {
        if (!StringUtils.hasText(toolCode) || toolVersion == null || toolVersion < 1
                || !toolCode.trim().matches("[A-Za-z0-9._-]{1,255}")) {
            return Optional.empty();
        }
        AiChatToolVersionEntity entity = versionMapper.selectOne(
                Wrappers.<AiChatToolVersionEntity>lambdaQuery()
                        .eq(AiChatToolVersionEntity::getToolCode, toolCode.trim())
                        .eq(AiChatToolVersionEntity::getVersionNo, toolVersion)
                        .eq(AiChatToolVersionEntity::getStatus, DefinitionStatus.PUBLISHED));
        if (entity == null || entity.getAdapterType() == null) {
            return Optional.empty();
        }
        Map<String, Object> stored = json.readMap(entity.getDefinitionJson());
        Map<String, Object> executable = executableDefinition(stored);
        return Optional.of(PublishedToolDefinition.builder()
                .toolCode(entity.getToolCode())
                .toolVersion(entity.getVersionNo())
                .adapterType(entity.getAdapterType().name())
                .checksum(entity.getChecksum())
                .definition(executable)
                .build());
    }

    /** Projects the canonical multi-binding document to the immutable Gateway execution contract. */
    private Map<String, Object> executableDefinition(Map<String, Object> stored) {
        Map<String, Object> result = new LinkedHashMap<>(stored);
        Object value = stored.get("bindings");
        if (!(value instanceof List<?> bindings)) {
            return result;
        }
        for (Object item : bindings) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            Map<String, Object> binding = new LinkedHashMap<>();
            raw.forEach((key, field) -> binding.put(String.valueOf(key), field));
            if (Boolean.FALSE.equals(binding.get("enabled"))) continue;
            String bindingType = String.valueOf(binding.getOrDefault("bindingType", "")).trim().toUpperCase();
            if (!"HTTP".equals(bindingType) && !"JAVA_INTERNAL".equals(bindingType)) continue;
            Object configValue = binding.get("config");
            if (configValue instanceof Map<?, ?> config) {
                config.forEach((key, field) -> result.put(String.valueOf(key), field));
            }
            result.put("endpoint", binding.get("endpointRef"));
            result.put("bindingType", bindingType);
            return result;
        }
        return result;
    }
}
