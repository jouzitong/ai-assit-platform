package ai.platform.aiassist.service.ai.meta.controller;

import ai.platform.aiassist.service.ai.api.AiMetaQueryApi;
import ai.platform.aiassist.service.ai.api.dto.AiMetaQueryRequest;
import ai.platform.aiassist.service.ai.api.dto.AiModelConfigDTO;
import ai.platform.aiassist.service.ai.api.dto.AiModelCredentialDTO;
import ai.platform.aiassist.service.ai.api.dto.AiProviderConfigDTO;
import ai.platform.aiassist.service.ai.meta.service.AiModelConfigService;
import ai.platform.aiassist.service.ai.meta.service.AiModelCredentialService;
import ai.platform.aiassist.service.ai.meta.service.AiProviderConfigService;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/meta")
public class AiMetaQueryController implements AiMetaQueryApi {

    private final AiProviderConfigService providerConfigService;
    private final AiModelConfigService modelConfigService;
    private final AiModelCredentialService credentialService;

    public AiMetaQueryController(AiProviderConfigService providerConfigService,
                                 AiModelConfigService modelConfigService,
                                 AiModelCredentialService credentialService) {
        this.providerConfigService = providerConfigService;
        this.modelConfigService = modelConfigService;
        this.credentialService = credentialService;
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
        return credentialService.queryAll(toInternalRequest(request)).stream()
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
        target.setCapabilityTags(source.getCapabilityTags());
        target.setMaxContextTokens(source.getMaxContextTokens());
        target.setMaxOutputTokens(source.getMaxOutputTokens());
        target.setTemperatureEnabled(source.getTemperatureEnabled());
        target.setEnabled(source.getEnabled());
        target.setPriority(source.getPriority());
        target.setRemark(source.getRemark());
        return target;
    }

    private AiModelCredentialDTO toCredentialDto(ai.platform.aiassist.service.ai.meta.entity.dto.AiModelCredentialDTO source) {
        AiModelCredentialDTO target = new AiModelCredentialDTO();
        target.setId(source.getId());
        target.setCredentialCode(source.getCredentialCode());
        target.setProviderCode(source.getProviderCode());
        target.setModelCode(source.getModelCode());
        target.setApiKeyMasked(source.getApiKeyMasked());
        target.setKeyVersion(source.getKeyVersion());
        target.setEnabled(source.getEnabled());
        target.setExpireAt(source.getExpireAt());
        target.setRemark(source.getRemark());
        return target;
    }
}
