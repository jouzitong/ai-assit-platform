package ai.platform.aiassit.execution.service.impl;

import ai.platform.aiassit.execution.convert.AiProviderRequestMapper;
import ai.platform.aiassit.execution.properties.AiCoreProperties;
import ai.platform.aiassit.execution.service.AiKnowledgeExecutionService;
import ai.platform.aiassit.execution.validator.AiRequestValidator;
import ai.platform.aiassit.service.ai.api.dto.EmbedRequest;
import ai.platform.aiassit.service.ai.api.dto.EmbedResponse;
import ai.platform.aiassit.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassit.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassit.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassit.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassit.service.ai.api.dto.KbUpsertRequest;
import ai.platform.aiassit.service.ai.api.dto.KbUpsertResponse;
import ai.platform.aiassit.service.ai.api.dto.RerankRequest;
import ai.platform.aiassit.service.ai.api.dto.RerankResponse;
import ai.platform.aiassit.service.ai.api.enums.ProviderType;
import ai.platform.aiassit.service.ai.spi.KnowledgeService;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultAiKnowledgeExecutionService implements AiKnowledgeExecutionService {

    private final Map<ProviderType, KnowledgeService> knowledgeServices = new EnumMap<>(ProviderType.class);
    private final AiCoreProperties properties;
    private final AiRequestValidator validator;
    private final AiProviderRequestMapper requestMapper;

    public DefaultAiKnowledgeExecutionService(List<KnowledgeService> knowledgeServices,
                                              AiCoreProperties properties,
                                              AiRequestValidator validator,
                                              AiProviderRequestMapper requestMapper) {
        for (KnowledgeService service : knowledgeServices) {
            this.knowledgeServices.put(service.providerType(), service);
        }
        this.properties = properties;
        this.validator = validator;
        this.requestMapper = requestMapper;
    }

    @Override
    public EmbedResponse embed(EmbedRequest request) {
        validator.validateEmbed(request);
        return resolveKnowledgeService(request.getProvider()).embed(requestMapper.mapEmbed(request, properties));
    }

    @Override
    public RerankResponse rerank(RerankRequest request) {
        validator.validateRerank(request);
        return resolveKnowledgeService(request.getProvider()).rerank(requestMapper.mapRerank(request, properties));
    }

    @Override
    public KbUpsertResponse kbUpsert(KbUpsertRequest request) {
        validator.validateKbUpsert(request);
        return resolveKnowledgeService(null).kbUpsert(requestMapper.mapKbUpsert(request));
    }

    @Override
    public KbDeleteResponse kbDelete(KbDeleteRequest request) {
        validator.validateKbDelete(request);
        return resolveKnowledgeService(null).kbDelete(requestMapper.mapKbDelete(request));
    }

    @Override
    public KbSearchResponse kbSearch(KbSearchRequest request) {
        validator.validateKbSearch(request);
        return resolveKnowledgeService(null).kbSearch(requestMapper.mapKbSearch(request));
    }

    private KnowledgeService resolveKnowledgeService(ProviderType requestedProvider) {
        ProviderType providerType = requestedProvider;
        if (providerType == null) {
            if (properties.isStrictProvider()) {
                throw new IllegalArgumentException("provider is required when ai.core.strict-provider=true");
            }
            providerType = properties.getDefaultProvider();
        }

        KnowledgeService service = knowledgeServices.get(providerType);
        if (service == null) {
            throw new IllegalStateException("knowledge service not found or not enabled: " + providerType);
        }
        return service;
    }
}
