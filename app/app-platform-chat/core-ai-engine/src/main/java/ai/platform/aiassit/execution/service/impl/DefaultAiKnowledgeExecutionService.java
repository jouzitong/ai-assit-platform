package ai.platform.aiassit.execution.service.impl;

import ai.platform.aiassit.execution.convert.AiProviderRequestMapper;
import ai.platform.aiassit.execution.properties.AiCoreProperties;
import ai.platform.aiassit.execution.service.AiKnowledgeExecutionService;
import ai.platform.aiassit.service.ai.api.AiVectorExecutionApi;
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
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.enums.ProviderType;
import ai.platform.aiassit.service.ai.spi.KnowledgeService;
import org.arthena.framework.common.exception.BizException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@RestController
public class DefaultAiKnowledgeExecutionService implements AiKnowledgeExecutionService, AiVectorExecutionApi {

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
    public EmbedResponse embed(@RequestBody EmbedRequest request) {
        validator.validateEmbed(request);
        return resolveKnowledgeService(request.getProvider()).embed(requestMapper.mapEmbed(request, properties));
    }

    @Override
    public RerankResponse rerank(@RequestBody RerankRequest request) {
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
                throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_PROVIDER);
            }
            providerType = properties.getDefaultProvider();
        }

        KnowledgeService service = knowledgeServices.get(providerType);
        if (service == null) {
            throw BizException.of(AiChatBizCodeConstant.KNOWLEDGE_SERVICE_NOT_FOUND, providerType);
        }
        return service;
    }
}
