package ai.platform.aiassit.execution.service.impl;

import ai.platform.aiassit.execution.service.AiClientModelDiscoveryService;
import ai.platform.aiassit.model.entity.dto.AiClientConfigDTO;
import ai.platform.aiassit.model.service.AiClientConfigService;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import ai.platform.aiassit.service.ai.spi.AiChatService;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderModel;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderModelListRequest;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultAiClientModelDiscoveryService implements AiClientModelDiscoveryService {
    private final Map<AiChatClientType, AiChatService> services = new EnumMap<>(AiChatClientType.class);
    private final AiClientConfigService clientConfigService;

    public DefaultAiClientModelDiscoveryService(List<AiChatService> services, AiClientConfigService clientConfigService) {
        services.forEach(service -> this.services.put(service.chatClientType(), service));
        this.clientConfigService = clientConfigService;
    }

    @Override
    public List<ProviderModel> listModels(Long clientId) {
        AiClientConfigDTO client = clientConfigService.require(clientId);
        AiChatService service = services.get(client.getClientType());
        if (service == null) throw BizException.of(AiChatBizCodeConstant.AI_CHAT_SERVICE_NOT_FOUND, client.getClientType());
        ProviderModelListRequest request = new ProviderModelListRequest();
        request.setBaseUrl(client.getBaseUrl()); request.setApiKey(client.getApiKey());
        Object timeout = client.getExtJson() == null ? null : client.getExtJson().get("modelListTimeoutMs");
        if (timeout instanceof Number number) request.setTimeoutMs(number.intValue());
        return service.listModels(request);
    }
}
