package ai.platform.aiassit.chat.meta.domainservice.impl;

import ai.platform.aiassit.chat.meta.domainservice.AiModelManageDomainService;
import ai.platform.aiassit.chat.meta.entity.dto.AiModelConfigDTO;
import ai.platform.aiassit.chat.meta.entity.dto.AiModelCredentialDTO;
import ai.platform.aiassit.chat.meta.entity.dto.AiModelManageDTO;
import ai.platform.aiassit.chat.meta.entity.req.AiMetaQueryRequest;
import ai.platform.aiassit.chat.meta.entity.req.AiModelManageQueryRequest;
import ai.platform.aiassit.chat.meta.mapper.AiModelManageMapper;
import ai.platform.aiassit.chat.meta.service.AiModelConfigService;
import ai.platform.aiassit.chat.meta.service.AiModelCredentialService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.athena.framework.data.jdbc.vo.PageInfo;
import org.athena.framework.data.jdbc.vo.PageResultVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AiModelManageDomainServiceImpl implements AiModelManageDomainService {

    private final AiModelManageMapper aiModelManageMapper;
    private final AiModelConfigService aiModelConfigService;
    private final AiModelCredentialService aiModelCredentialService;

    public AiModelManageDomainServiceImpl(AiModelManageMapper aiModelManageMapper,
                                          AiModelConfigService aiModelConfigService,
                                          AiModelCredentialService aiModelCredentialService) {
        this.aiModelManageMapper = aiModelManageMapper;
        this.aiModelConfigService = aiModelConfigService;
        this.aiModelCredentialService = aiModelCredentialService;
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
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiModelManageDTO add(AiModelManageDTO dto) {
        validateModelPayload(dto);

        AiModelConfigDTO createdModel = aiModelConfigService.add(toModelConfigDTO(dto));
        if (createdModel == null) {
            throw new IllegalStateException("新增模型配置失败");
        }

        if (hasCredentialPayload(dto)) {
            AiModelCredentialDTO createdCredential = aiModelCredentialService.add(
                    toCreateCredentialDTO(createdModel, dto)
            );
            if (createdCredential == null) {
                throw new IllegalStateException("新增模型凭证失败");
            }
        }

        return get(createdModel.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiModelManageDTO update(Long id, AiModelManageDTO dto) {
        AiModelManageDTO current = get(id);
        AiModelManageDTO payload = mergeForUpdate(current, dto, true);
        validateModelPayload(payload);

        AiModelConfigDTO updatedModel = aiModelConfigService.update(id, toModelConfigDTO(payload));
        if (updatedModel == null) {
            throw new IllegalStateException("更新模型配置失败");
        }

        syncCredential(payload, updatedModel, true);
        return get(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiModelManageDTO edit(Long id, AiModelManageDTO dto) {
        AiModelManageDTO current = get(id);
        AiModelManageDTO payload = mergeForUpdate(current, dto, false);
        validateModelPayload(payload);

        AiModelConfigDTO editedModel = aiModelConfigService.edit(id, toModelConfigDTO(payload));
        if (editedModel == null) {
            throw new IllegalStateException("编辑模型配置失败");
        }

        syncCredential(payload, editedModel, false);
        return get(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        AiModelManageDTO current = get(id);
        AiMetaQueryRequest query = new AiMetaQueryRequest();
        query.setPage(1);
        query.setSize(200);
        query.setModelCode(current.getModelCode());

        List<AiModelCredentialDTO> credentialList = aiModelCredentialService.queryAll(query);
        for (AiModelCredentialDTO credential : credentialList) {
            aiModelCredentialService.delete(credential.getId());
        }
        return aiModelConfigService.delete(id);
    }

    private void syncCredential(AiModelManageDTO payload, AiModelConfigDTO model, boolean replaceNulls) {
        Long credentialId = payload.getCredentialId();
        boolean shouldCreate = credentialId == null && hasCredentialPayload(payload);
        boolean shouldUpdate = credentialId != null && hasCredentialMutation(payload);

        if (shouldCreate) {
            AiModelCredentialDTO createdCredential = aiModelCredentialService.add(toCreateCredentialDTO(model, payload));
            if (createdCredential == null) {
                throw new IllegalStateException("新增模型凭证失败");
            }
            return;
        }

        if (!shouldUpdate) {
            return;
        }

        AiModelCredentialDTO existing = aiModelCredentialService.get(credentialId);
        if (existing == null) {
            throw new IllegalStateException("模型凭证不存在");
        }

        AiModelCredentialDTO credentialPayload = toUpdateCredentialDTO(existing, model, payload, replaceNulls);
        AiModelCredentialDTO updatedCredential = replaceNulls
                ? aiModelCredentialService.update(credentialId, credentialPayload)
                : aiModelCredentialService.edit(credentialId, credentialPayload);
        if (updatedCredential == null) {
            throw new IllegalStateException("更新模型凭证失败");
        }
    }

    private AiModelConfigDTO toModelConfigDTO(AiModelManageDTO dto) {
        AiModelConfigDTO model = new AiModelConfigDTO();
        model.setId(dto.getId());
        model.setModelCode(trimToNull(dto.getModelCode()));
        model.setModelName(trimToNull(dto.getModelName()));
        model.setProviderCode(trimToNull(dto.getProviderCode()));
        model.setApiModel(trimToNull(dto.getApiModel()));
        model.setCapabilityTags(trimToNull(dto.getCapabilityTags()));
        model.setMaxContextTokens(dto.getMaxContextTokens());
        model.setMaxOutputTokens(dto.getMaxOutputTokens());
        model.setTemperatureEnabled(dto.getTemperatureEnabled());
        model.setEnabled(dto.getEnabled());
        model.setPriority(dto.getPriority());
        model.setRemark(trimToNull(dto.getRemark()));
        return model;
    }

    private AiModelCredentialDTO toCreateCredentialDTO(AiModelConfigDTO model, AiModelManageDTO dto) {
        String apiKey = trimToNull(dto.getApiKeyInput());
        if (!StringUtils.hasText(dto.getCredentialCode()) || !StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("新增模型凭证时必须提供凭证编码和 API Key");
        }

        AiModelCredentialDTO credential = new AiModelCredentialDTO();
        credential.setCredentialCode(trimToNull(dto.getCredentialCode()));
        credential.setProviderCode(model.getProviderCode());
        credential.setModelCode(model.getModelCode());
        credential.setApiKeyCiphertext(apiKey);
        credential.setApiKeyMasked(maskApiKey(apiKey));
        credential.setKeyVersion(dto.getKeyVersion() == null ? 1 : dto.getKeyVersion());
        credential.setEnabled(dto.getCredentialEnabled() != null ? dto.getCredentialEnabled() : model.getEnabled());
        credential.setExpireAt(dto.getExpireAt());
        credential.setRemark(trimToNull(dto.getCredentialRemark()));
        return credential;
    }

    private AiModelCredentialDTO toUpdateCredentialDTO(AiModelCredentialDTO existing,
                                                       AiModelConfigDTO model,
                                                       AiModelManageDTO payload,
                                                       boolean replaceNulls) {
        AiModelCredentialDTO credential = new AiModelCredentialDTO();
        credential.setId(existing.getId());
        credential.setCredentialCode(
                chooseValue(trimToNull(payload.getCredentialCode()), existing.getCredentialCode(), replaceNulls)
        );
        credential.setProviderCode(model.getProviderCode());
        credential.setModelCode(model.getModelCode());

        String apiKey = trimToNull(payload.getApiKeyInput());
        if (StringUtils.hasText(apiKey)) {
            credential.setApiKeyCiphertext(apiKey);
            credential.setApiKeyMasked(maskApiKey(apiKey));
        } else if (replaceNulls) {
            credential.setApiKeyCiphertext(existing.getApiKeyCiphertext());
            credential.setApiKeyMasked(existing.getApiKeyMasked());
        }

        credential.setKeyVersion(chooseValue(payload.getKeyVersion(), existing.getKeyVersion(), replaceNulls));
        credential.setEnabled(
                chooseValue(
                        payload.getCredentialEnabled(),
                        existing.getEnabled() != null ? existing.getEnabled() : model.getEnabled(),
                        replaceNulls
                )
        );
        credential.setExpireAt(chooseValue(payload.getExpireAt(), existing.getExpireAt(), replaceNulls));
        credential.setRemark(
                chooseValue(trimToNull(payload.getCredentialRemark()), existing.getRemark(), replaceNulls)
        );
        return credential;
    }

    private AiModelManageDTO mergeForUpdate(AiModelManageDTO current, AiModelManageDTO incoming, boolean replaceNulls) {
        AiModelManageDTO merged = new AiModelManageDTO();
        merged.setId(current.getId());
        merged.setModelCode(chooseValue(trimToNull(incoming.getModelCode()), current.getModelCode(), replaceNulls));
        merged.setModelName(chooseValue(trimToNull(incoming.getModelName()), current.getModelName(), replaceNulls));
        merged.setProviderCode(chooseValue(trimToNull(incoming.getProviderCode()), current.getProviderCode(), replaceNulls));
        merged.setProviderName(current.getProviderName());
        merged.setApiModel(chooseValue(trimToNull(incoming.getApiModel()), current.getApiModel(), replaceNulls));
        merged.setCapabilityTags(
                chooseValue(trimToNull(incoming.getCapabilityTags()), current.getCapabilityTags(), replaceNulls)
        );
        merged.setMaxContextTokens(chooseValue(incoming.getMaxContextTokens(), current.getMaxContextTokens(), replaceNulls));
        merged.setMaxOutputTokens(chooseValue(incoming.getMaxOutputTokens(), current.getMaxOutputTokens(), replaceNulls));
        merged.setTemperatureEnabled(
                chooseValue(incoming.getTemperatureEnabled(), current.getTemperatureEnabled(), replaceNulls)
        );
        merged.setEnabled(chooseValue(incoming.getEnabled(), current.getEnabled(), replaceNulls));
        merged.setPriority(chooseValue(incoming.getPriority(), current.getPriority(), replaceNulls));
        merged.setRemark(chooseValue(trimToNull(incoming.getRemark()), current.getRemark(), replaceNulls));
        merged.setCredentialId(current.getCredentialId());
        merged.setCredentialCode(
                chooseValue(trimToNull(incoming.getCredentialCode()), current.getCredentialCode(), replaceNulls)
        );
        merged.setApiKeyInput(trimToNull(incoming.getApiKeyInput()));
        merged.setApiKeyMasked(current.getApiKeyMasked());
        merged.setKeyVersion(chooseValue(incoming.getKeyVersion(), current.getKeyVersion(), replaceNulls));
        merged.setCredentialEnabled(
                chooseValue(incoming.getCredentialEnabled(), current.getCredentialEnabled(), replaceNulls)
        );
        merged.setExpireAt(chooseValue(incoming.getExpireAt(), current.getExpireAt(), replaceNulls));
        merged.setCredentialRemark(
                chooseValue(trimToNull(incoming.getCredentialRemark()), current.getCredentialRemark(), replaceNulls)
        );
        return merged;
    }

    private boolean hasCredentialPayload(AiModelManageDTO dto) {
        return StringUtils.hasText(dto.getCredentialCode())
                || StringUtils.hasText(dto.getApiKeyInput())
                || dto.getKeyVersion() != null
                || dto.getCredentialEnabled() != null
                || dto.getExpireAt() != null
                || StringUtils.hasText(dto.getCredentialRemark());
    }

    private boolean hasCredentialMutation(AiModelManageDTO dto) {
        return hasCredentialPayload(dto);
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
        if (!StringUtils.hasText(dto.getApiModel())) {
            throw new IllegalArgumentException("Provider 模型标识不能为空");
        }
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

    private <T> T chooseValue(T incoming, T current, boolean replaceNulls) {
        if (replaceNulls) {
            return incoming;
        }
        return incoming != null ? incoming : current;
    }
}
