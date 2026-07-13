package ai.platform.aiassit.execution.service;

import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderModel;

import java.util.List;

public interface AiClientModelDiscoveryService {
    List<ProviderModel> listModels(Long clientId);
}
