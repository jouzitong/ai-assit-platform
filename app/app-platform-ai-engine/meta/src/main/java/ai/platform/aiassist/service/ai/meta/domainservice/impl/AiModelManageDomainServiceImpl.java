package ai.platform.aiassist.service.ai.meta.domainservice.impl;

import ai.platform.aiassist.service.ai.meta.domainservice.AiModelManageDomainService;
import ai.platform.aiassist.service.ai.meta.entity.dto.AiModelConfigDTO;
import ai.platform.aiassist.service.ai.meta.entity.dto.AiModelManageDTO;
import ai.platform.aiassist.service.ai.meta.entity.req.AiModelManageQueryRequest;
import ai.platform.aiassist.service.ai.meta.mapper.AiModelManageMapper;
import ai.platform.aiassist.service.ai.meta.service.AiModelConfigService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.athena.framework.data.jdbc.vo.PageInfo;
import org.athena.framework.data.jdbc.vo.PageResultVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiModelManageDomainServiceImpl implements AiModelManageDomainService {

    private final AiModelManageMapper aiModelManageMapper;
    private final AiModelConfigService aiModelConfigService;

    public AiModelManageDomainServiceImpl(AiModelManageMapper aiModelManageMapper,
                                          AiModelConfigService aiModelConfigService) {
        this.aiModelManageMapper = aiModelManageMapper;
        this.aiModelConfigService = aiModelConfigService;
    }

    @Override
    public PageResultVO<AiModelManageDTO> page(AiModelManageQueryRequest query) {
        AiModelManageQueryRequest safeQuery = query == null ? new AiModelManageQueryRequest() : query;
        Page<AiModelManageDTO> page = Page.of(safeQuery.page(), safeQuery.size());
        List<AiModelManageDTO> records = aiModelManageMapper.pageAggregate(page, safeQuery);
        PageInfo pageInfo = new PageInfo(page.getTotal(), safeQuery.size(), safeQuery.page());
        return PageResultVO.of(records, pageInfo);
    }

    @Override
    public AiModelManageDTO get(Long id) {
        AiModelManageDTO dto = aiModelManageMapper.selectByModelId(id);
        if (dto == null) {
            throw new IllegalStateException("模型配置不存在");
        }
        dto.setApiKeyInput(null);
        if (dto.getApiKeyMasked() == null) {
            AiModelConfigDTO current = aiModelConfigService.get(id);
            dto.setApiKeyMasked(current == null ? null : maskApiKey(current.getApiKey()));
        }
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiModelManageDTO add(AiModelManageDTO dto) {
        validateModelPayload(dto);

        AiModelConfigDTO createdModel = aiModelConfigService.add(toModelConfigDTO(dto, null, true));
        if (createdModel == null) {
            throw new IllegalStateException("新增模型配置失败");
        }
        return get(createdModel.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiModelManageDTO update(Long id, AiModelManageDTO dto) {
        AiModelManageDTO current = get(id);
        AiModelConfigDTO currentModel = requireModel(id);
        AiModelManageDTO payload = mergeForUpdate(current, dto, true);
        validateModelPayload(payload);

        AiModelConfigDTO updatedModel = aiModelConfigService.update(id, toModelConfigDTO(payload, currentModel, true));
        if (updatedModel == null) {
            throw new IllegalStateException("更新模型配置失败");
        }
        return get(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiModelManageDTO edit(Long id, AiModelManageDTO dto) {
        AiModelManageDTO current = get(id);
        AiModelConfigDTO currentModel = requireModel(id);
        AiModelManageDTO payload = mergeForUpdate(current, dto, false);
        validateModelPayload(payload);

        AiModelConfigDTO editedModel = aiModelConfigService.edit(id, toModelConfigDTO(payload, currentModel, false));
        if (editedModel == null) {
            throw new IllegalStateException("编辑模型配置失败");
        }
        return get(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        return aiModelConfigService.delete(id);
    }

    private AiModelConfigDTO toModelConfigDTO(AiModelManageDTO dto, AiModelConfigDTO current, boolean replaceNulls) {
        AiModelConfigDTO model = new AiModelConfigDTO();
        model.setId(dto.getId());
        model.setModelCode(trimToNull(dto.getModelCode()));
        model.setModelName(trimToNull(dto.getModelName()));
        model.setProviderCode(trimToNull(dto.getProviderCode()));
        model.setProviderName(trimToNull(dto.getProviderName()));
        model.setBaseUrl(trimToNull(dto.getBaseUrl()));
        model.setApiModel(trimToNull(dto.getApiModel()));
        model.setEnabled(dto.getEnabled());
        String apiKey = trimToNull(dto.getApiKeyInput());
        if (StringUtils.hasText(apiKey)) {
            model.setApiKey(apiKey);
        } else if (current != null) {
            model.setApiKey(current.getApiKey());
        }
        model.setExtJson(chooseValue(copyMap(dto.getExtJson()), current == null ? null : current.getExtJson(), replaceNulls));
        return model;
    }

    private AiModelManageDTO mergeForUpdate(AiModelManageDTO current, AiModelManageDTO incoming, boolean replaceNulls) {
        AiModelManageDTO merged = new AiModelManageDTO();
        merged.setId(current.getId());
        merged.setModelCode(chooseValue(trimToNull(incoming.getModelCode()), current.getModelCode(), replaceNulls));
        merged.setModelName(chooseValue(trimToNull(incoming.getModelName()), current.getModelName(), replaceNulls));
        merged.setProviderCode(chooseValue(trimToNull(incoming.getProviderCode()), current.getProviderCode(), replaceNulls));
        merged.setProviderName(chooseValue(trimToNull(incoming.getProviderName()), current.getProviderName(), replaceNulls));
        merged.setBaseUrl(chooseValue(trimToNull(incoming.getBaseUrl()), current.getBaseUrl(), replaceNulls));
        merged.setApiModel(chooseValue(trimToNull(incoming.getApiModel()), current.getApiModel(), replaceNulls));
        merged.setEnabled(chooseValue(incoming.getEnabled(), current.getEnabled(), replaceNulls));
        merged.setApiKeyInput(trimToNull(incoming.getApiKeyInput()));
        merged.setApiKeyMasked(current.getApiKeyMasked());
        merged.setExtJson(chooseValue(copyMap(incoming.getExtJson()), copyMap(current.getExtJson()), replaceNulls));
        return merged;
    }

    private void validateModelPayload(AiModelManageDTO dto) {
        if (!StringUtils.hasText(dto.getModelCode())) {
            throw new IllegalArgumentException("模型编码不能为空");
        }
        if (!StringUtils.hasText(dto.getModelName())) {
            throw new IllegalArgumentException("模型名称不能为空");
        }
        if (!StringUtils.hasText(dto.getProviderCode())) {
            throw new IllegalArgumentException("所属 Provider 不能为空");
        }
        if (!StringUtils.hasText(dto.getProviderName())) {
            throw new IllegalArgumentException("Provider 名称不能为空");
        }
        if (!StringUtils.hasText(dto.getBaseUrl())) {
            throw new IllegalArgumentException("基础地址不能为空");
        }
        if (!StringUtils.hasText(dto.getApiModel())) {
            throw new IllegalArgumentException("Provider 模型标识不能为空");
        }
        if (dto.getId() == null && !StringUtils.hasText(dto.getApiKeyInput())) {
            throw new IllegalArgumentException("新增模型凭证时必须提供 API Key");
        }
    }

    private AiModelConfigDTO requireModel(Long id) {
        AiModelConfigDTO current = aiModelConfigService.get(id);
        if (current == null) {
            throw new IllegalStateException("模型配置不存在");
        }
        return current;
    }

    private String maskApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        String trimmed = apiKey.trim();
        int visiblePrefix = Math.min(4, trimmed.length());
        int visibleSuffix = trimmed.length() > 8 ? 4 : 1;
        if (trimmed.length() <= visiblePrefix + visibleSuffix) {
            return trimmed.charAt(0) + "***" + trimmed.charAt(trimmed.length() - 1);
        }
        return trimmed.substring(0, visiblePrefix) + "****" + trimmed.substring(trimmed.length() - visibleSuffix);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? null : new LinkedHashMap<>(source);
    }

    private <T> T chooseValue(T incoming, T current, boolean replaceNulls) {
        if (replaceNulls) {
            return incoming;
        }
        return incoming != null ? incoming : current;
    }
}
