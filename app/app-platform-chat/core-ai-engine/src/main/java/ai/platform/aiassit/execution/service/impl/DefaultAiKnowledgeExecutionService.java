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
import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import ai.platform.aiassit.service.ai.spi.KnowledgeService;
import ai.platform.aiassit.knowledge.manage.entity.dto.AiKbStoreDTO;
import ai.platform.aiassit.knowledge.manage.service.AiKbStoreService;
import org.arthena.framework.common.exception.BizException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class DefaultAiKnowledgeExecutionService implements AiKnowledgeExecutionService, AiVectorExecutionApi {

    private final Map<AiKnowledgeClientType, KnowledgeService> knowledgeServices = new EnumMap<>(AiKnowledgeClientType.class);
    private final AiCoreProperties properties;
    private final AiRequestValidator validator;
    private final AiProviderRequestMapper requestMapper;
    private final AiKbStoreService kbStoreService;

    public DefaultAiKnowledgeExecutionService(List<KnowledgeService> knowledgeServices,
                                              AiCoreProperties properties,
                                              AiRequestValidator validator,
                                              AiProviderRequestMapper requestMapper,
                                              AiKbStoreService kbStoreService) {
        for (KnowledgeService service : knowledgeServices) {
            this.knowledgeServices.put(service.knowledgeClientType(), service);
        }
        this.properties = properties;
        this.validator = validator;
        this.requestMapper = requestMapper;
        this.kbStoreService = kbStoreService;
    }

    @Override
    public EmbedResponse embed(@RequestBody EmbedRequest request) {
        validator.validateEmbed(request);
        return resolveKnowledgeService(request.getClientType()).embed(requestMapper.mapEmbed(request, properties));
    }

    @Override
    public RerankResponse rerank(@RequestBody RerankRequest request) {
        validator.validateRerank(request);
        return resolveKnowledgeService(request.getClientType()).rerank(requestMapper.mapRerank(request, properties));
    }

    @Override
    public KbUpsertResponse kbUpsert(KbUpsertRequest request) {
        validator.validateKbUpsert(request);
        AiKbStoreDTO store = requireKnowledgeStore(request.getKbId());
        request.setMeta(mergeStoreMeta(store, request.getMeta()));
        request.setKbId(store.getProviderKbId());
        return resolveKnowledgeService(store.getClientType()).kbUpsert(requestMapper.mapKbUpsert(request));
    }

    @Override
    public KbDeleteResponse kbDelete(KbDeleteRequest request) {
        validator.validateKbDelete(request);
        AiKbStoreDTO store = requireKnowledgeStore(request.getKbId());
        request.setMeta(mergeStoreMeta(store, request.getMeta()));
        request.setKbId(store.getProviderKbId());
        return resolveKnowledgeService(store.getClientType()).kbDelete(requestMapper.mapKbDelete(request));
    }

    @Override
    public KbSearchResponse kbSearch(KbSearchRequest request) {
        validator.validateKbSearch(request);
        AiKbStoreDTO store = requireKnowledgeStore(request.getKbId());
        request.setMeta(mergeStoreMeta(store, request.getMeta()));
        request.setKbId(store.getProviderKbId());
        return resolveKnowledgeService(store.getClientType()).kbSearch(requestMapper.mapKbSearch(request));
    }

    private AiKbStoreDTO requireKnowledgeStore(String kbCode) {
        AiKbStoreDTO store = kbStoreService.getByKbCode(kbCode);
        if (store == null) {
            throw BizException.of(AiChatBizCodeConstant.KNOWLEDGE_SERVICE_NOT_FOUND, kbCode);
        }
        if (!Boolean.TRUE.equals(store.getEnabled())) {
            throw BizException.of(AiChatBizCodeConstant.KNOWLEDGE_SERVICE_NOT_FOUND, kbCode);
        }
        if (store.getClientType() == null) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_KNOWLEDGE_CLIENT_TYPE);
        }
        return store;
    }

    private RequestMeta mergeStoreMeta(AiKbStoreDTO store, RequestMeta requestMeta) {
        RequestMeta merged = requestMeta == null ? new RequestMeta() : requestMeta;
        Map<String, Object> ext = new LinkedHashMap<>();
        if (merged.getExt() != null) {
            ext.putAll(merged.getExt());
        }
        if (store.getExtJson() != null) {
            ext.putAll(store.getExtJson());
        }
        if (StringUtils.hasText(store.getUrl())) {
            ext.put("kbEndpoint", store.getUrl().trim());
        }
        ext.put("localKbCode", store.getKbCode());
        ext.put("providerKbId", store.getProviderKbId());
        merged.setExt(ext);
        return merged;
    }

    private KnowledgeService resolveKnowledgeService(AiKnowledgeClientType requestedClientType) {
        AiKnowledgeClientType clientType = requestedClientType;
        if (clientType == null) {
            if (properties.isStrictClientType()) {
                throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_KNOWLEDGE_CLIENT_TYPE);
            }
            clientType = properties.getDefaultKnowledgeClientType();
        }

        KnowledgeService service = knowledgeServices.get(clientType);
        if (service == null) {
            throw BizException.of(AiChatBizCodeConstant.KNOWLEDGE_SERVICE_NOT_FOUND, clientType);
        }
        return service;
    }
}
