package ai.platform.aiassist.service.ai.meta.controller;

import ai.platform.aiassist.service.ai.api.AiMetaQueryApi;
import ai.platform.aiassist.service.ai.api.dto.AiMetaQueryRequest;
import ai.platform.aiassist.service.ai.api.dto.AiModelConfigDTO;
import ai.platform.aiassist.service.ai.api.dto.AiModelCredentialDTO;
import ai.platform.aiassist.service.ai.api.dto.AiProviderConfigDTO;
import ai.platform.aiassist.service.ai.api.dto.AiProviderModelOverviewDTO;
import ai.platform.aiassist.service.ai.meta.service.AiModelConfigService;
import ai.platform.aiassist.service.ai.meta.service.AiProviderConfigService;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
//@RequestMapping("/api/v1/ai/meta")
public class AiMetaQueryController implements AiMetaQueryApi {

    private final AiProviderConfigService providerConfigService;
    private final AiModelConfigService modelConfigService;

    public AiMetaQueryController(AiProviderConfigService providerConfigService,
                                 AiModelConfigService modelConfigService) {
        this.providerConfigService = providerConfigService;
        this.modelConfigService = modelConfigService;
    }

    @Override
    public AiProviderModelOverviewDTO providerModelOverview(@RequestBody(required = false) AiMetaQueryRequest request) {
        ai.platform.aiassist.service.ai.meta.entity.req.AiMetaQueryRequest internalRequest = toInternalRequest(request);
        List<ai.platform.aiassist.service.ai.meta.entity.dto.AiProviderConfigDTO> providers =
                providerConfigService.queryAll(internalRequest);
        List<ai.platform.aiassist.service.ai.meta.entity.dto.AiModelConfigDTO> models =
                modelConfigService.queryAll(internalRequest);
        Map<String, List<ai.platform.aiassist.service.ai.meta.entity.dto.AiModelConfigDTO>> modelMap = models.stream()
                .collect(Collectors.groupingBy(ai.platform.aiassist.service.ai.meta.entity.dto.AiModelConfigDTO::getProviderCode));

        AiProviderModelOverviewDTO response = new AiProviderModelOverviewDTO();
        response.setProviders(providers.stream()
                .map(provider -> toOverviewProviderItem(provider, modelMap.get(provider.getProviderCode())))
                .toList());
        response.setProviderCount(providers.size());
        response.setModelCount(models.size());
        return response;
    }

    @Override
    public List<AiProviderConfigDTO> listProviders(@RequestBody(required = false) AiMetaQueryRequest request) {
        return providerConfigService.queryAll(toInternalRequest(request)).stream()
                .map(this::toProviderDto)
                .toList();
    }

    @Override
    public List<AiModelConfigDTO> listModels(@RequestBody(required = false) AiMetaQueryRequest request) {
        return modelConfigService.queryAll(toInternalRequest(request)).stream()
                .map(this::toModelDto)
                .toList();
    }

    @Override
    public List<AiModelCredentialDTO> listCredentials(@RequestBody(required = false) AiMetaQueryRequest request) {
        ai.platform.aiassist.service.ai.meta.entity.req.AiMetaQueryRequest internalRequest = toInternalRequest(request);
        if (internalRequest != null) {
            internalRequest.setEnabled(null);
        }
        return modelConfigService.queryAll(internalRequest).stream()
                .filter(this::hasCredential)
                .filter(model -> request == null || request.getEnabled() == null
                        || request.getEnabled().equals(model.getEnabled()))
                .map(this::toCredentialDto)
                .toList();
    }

    private ai.platform.aiassist.service.ai.meta.entity.req.AiMetaQueryRequest toInternalRequest(AiMetaQueryRequest request) {
        ai.platform.aiassist.service.ai.meta.entity.req.AiMetaQueryRequest target =
                new ai.platform.aiassist.service.ai.meta.entity.req.AiMetaQueryRequest();
        if (request != null) {
            target.setProviderCode(request.getProviderCode());
            target.setModelCode(request.getModelCode());
            target.setEnabled(request.getEnabled());
        }
        return target;
    }

    private AiProviderConfigDTO toProviderDto(ai.platform.aiassist.service.ai.meta.entity.dto.AiProviderConfigDTO source) {
        AiProviderConfigDTO target = new AiProviderConfigDTO();
        target.setId(source.getId());
        target.setProviderCode(source.getProviderCode());
        target.setProviderName(source.getProviderName());
        target.setBaseUrl(source.getBaseUrl());
        target.setConnectTimeoutMs(source.getConnectTimeoutMs());
        target.setReadTimeoutMs(source.getReadTimeoutMs());
        target.setEnabled(source.getEnabled());
        target.setRemark(source.getRemark());
        return target;
    }

    private AiModelConfigDTO toModelDto(ai.platform.aiassist.service.ai.meta.entity.dto.AiModelConfigDTO source) {
        AiModelConfigDTO target = new AiModelConfigDTO();
        target.setId(source.getId());
        target.setModelCode(source.getModelCode());
        target.setModelName(source.getModelName());
        target.setProviderCode(source.getProviderCode());
        target.setApiModel(source.getApiModel());
        target.setCapabilityTags(extText(source.getExtJson(), "capabilityTags"));
        target.setMaxContextTokens(extInteger(source.getExtJson(), "maxContextTokens"));
        target.setMaxOutputTokens(extInteger(source.getExtJson(), "maxOutputTokens"));
        target.setTemperatureEnabled(extInteger(source.getExtJson(), "temperatureEnabled"));
        target.setEnabled(source.getEnabled());
        target.setPriority(extInteger(source.getExtJson(), "priority"));
        target.setExtJson(copyMap(source.getExtJson()));
        target.setRemark(extText(source.getExtJson(), "remark"));
        return target;
    }

    private AiModelCredentialDTO toCredentialDto(ai.platform.aiassist.service.ai.meta.entity.dto.AiModelConfigDTO source) {
        AiModelCredentialDTO target = new AiModelCredentialDTO();
        target.setId(source.getId());
        target.setCredentialCode(null);
        target.setProviderCode(source.getProviderCode());
        target.setModelCode(source.getModelCode());
        target.setApiKeyMasked(maskApiKey(source.getApiKey()));
        target.setKeyVersion(null);
        target.setEnabled(source.getEnabled());
        target.setExpireAt(null);
        target.setRemark(null);
        return target;
    }

    private boolean hasCredential(ai.platform.aiassist.service.ai.meta.entity.dto.AiModelConfigDTO source) {
        return source != null && source.getApiKey() != null;
    }

    private AiProviderModelOverviewDTO.ProviderItem toOverviewProviderItem(
            ai.platform.aiassist.service.ai.meta.entity.dto.AiProviderConfigDTO source,
            List<ai.platform.aiassist.service.ai.meta.entity.dto.AiModelConfigDTO> models) {
        List<ai.platform.aiassist.service.ai.meta.entity.dto.AiModelConfigDTO> safeModels =
                models == null ? Collections.emptyList() : models;
        AiProviderModelOverviewDTO.ProviderItem target = new AiProviderModelOverviewDTO.ProviderItem();
        target.setId(source.getId());
        target.setProviderCode(source.getProviderCode());
        target.setProviderName(source.getProviderName());
        target.setBaseUrl(source.getBaseUrl());
        target.setConnectTimeoutMs(source.getConnectTimeoutMs());
        target.setReadTimeoutMs(source.getReadTimeoutMs());
        target.setEnabled(source.getEnabled());
        target.setRemark(source.getRemark());
        target.setModelCount(safeModels.size());
        target.setModels(safeModels.stream()
                .map(this::toOverviewModelItem)
                .toList());
        return target;
    }

    private AiProviderModelOverviewDTO.ModelItem toOverviewModelItem(
            ai.platform.aiassist.service.ai.meta.entity.dto.AiModelConfigDTO source) {
        AiProviderModelOverviewDTO.ModelItem target = new AiProviderModelOverviewDTO.ModelItem();
        target.setId(source.getId());
        target.setModelCode(source.getModelCode());
        target.setModelName(source.getModelName());
        target.setProviderCode(source.getProviderCode());
        target.setApiModel(source.getApiModel());
        target.setCapabilityTags(extText(source.getExtJson(), "capabilityTags"));
        target.setMaxContextTokens(extInteger(source.getExtJson(), "maxContextTokens"));
        target.setMaxOutputTokens(extInteger(source.getExtJson(), "maxOutputTokens"));
        target.setTemperatureEnabled(extInteger(source.getExtJson(), "temperatureEnabled"));
        target.setEnabled(source.getEnabled());
        target.setPriority(extInteger(source.getExtJson(), "priority"));
        target.setExtJson(copyMap(source.getExtJson()));
        target.setRemark(extText(source.getExtJson(), "remark"));
        return target;
    }

    private Integer extInteger(Map<String, Object> ext, String key) {
        if (ext == null) {
            return null;
        }
        Object value = ext.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String extText(Map<String, Object> ext, String key) {
        if (ext == null) {
            return null;
        }
        Object value = ext.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Map<String, Object> copyMap(Map<String, Object> ext) {
        return ext == null ? null : new LinkedHashMap<>(ext);
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
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
}
