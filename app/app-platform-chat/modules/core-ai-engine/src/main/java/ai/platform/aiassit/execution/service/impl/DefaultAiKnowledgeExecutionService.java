package ai.platform.aiassit.execution.service.impl;

import ai.platform.aiassit.execution.convert.AiProviderRequestMapper;
import ai.platform.aiassit.execution.properties.AiCoreProperties;
import ai.platform.aiassit.execution.service.KnowledgeClientConfigService;
import ai.platform.aiassit.execution.service.AiKnowledgeExecutionService;
import ai.platform.aiassit.service.ai.api.AiVectorExecutionApi;
import ai.platform.aiassit.execution.validator.AiRequestValidator;
import ai.platform.aiassit.service.ai.api.dto.EmbedRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbAuthConfig;
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
import ai.platform.aiassit.service.ai.api.enums.AiKbAuthType;
import ai.platform.aiassit.service.ai.api.enums.AiKbStoreSyncStatus;
import ai.platform.aiassit.service.ai.spi.KnowledgeService;
import ai.platform.aiassit.knowledge.manage.entity.store.dto.AiKbStoreDTO;
import ai.platform.aiassit.knowledge.manage.service.AiKbStoreService;
import org.arthena.framework.common.exception.BizException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库提供方调用的统一执行入口。
 *
 * <p>在调用向量化、重排、文档写入、删除或检索前校验请求，并将本地知识库编码、客户端配置和认证快照合并为提供方请求；响应避免暴露提供方 Dataset 标识。</p>
 */
@RestController
public class DefaultAiKnowledgeExecutionService implements AiKnowledgeExecutionService, AiVectorExecutionApi {

    private final Map<AiKnowledgeClientType, KnowledgeService> knowledgeServices = new EnumMap<>(AiKnowledgeClientType.class);
    private final AiCoreProperties properties;
    private final AiRequestValidator validator;
    private final AiProviderRequestMapper requestMapper;
    private final AiKbStoreService kbStoreService;
    private final KnowledgeClientConfigService knowledgeClientConfigService;

    public DefaultAiKnowledgeExecutionService(List<KnowledgeService> knowledgeServices,
                                              AiCoreProperties properties,
                                              AiRequestValidator validator,
                                              AiProviderRequestMapper requestMapper,
                                              AiKbStoreService kbStoreService,
                                              KnowledgeClientConfigService knowledgeClientConfigService) {
        for (KnowledgeService service : knowledgeServices) {
            this.knowledgeServices.put(service.knowledgeClientType(), service);
        }
        this.properties = properties;
        this.validator = validator;
        this.requestMapper = requestMapper;
        this.kbStoreService = kbStoreService;
        this.knowledgeClientConfigService = knowledgeClientConfigService;
    }

    /**
     * 将输入文本转换为向量表示。
     *
     * @param request 向量化请求体，包含文本、可选客户端类型和调用元数据
     * @return 向量化结果，包含每段文本对应的向量和执行信息
     */
    @Override
    public EmbedResponse embed(@RequestBody EmbedRequest request) {
        validator.validateEmbed(request);
        return resolveKnowledgeService(request.getClientType()).embed(requestMapper.mapEmbed(request, properties));
    }

    /**
     * 根据查询文本对候选文本进行相关性重排。
     *
     * @param request 重排请求体，包含查询、候选文本、客户端选择和调用元数据
     * @return 重排结果，包含候选项排序和相关性评分
     */
    @Override
    public RerankResponse rerank(@RequestBody RerankRequest request) {
        validator.validateRerank(request);
        return resolveKnowledgeService(request.getClientType()).rerank(requestMapper.mapRerank(request, properties));
    }

    /**
     * 将本地知识库文档写入已绑定的提供方 Dataset。
     *
     * @param request 文档写入请求，使用本地知识库编码和待写入内容
     * @return 提供方写入结果，已合并本地配置与认证信息
     */
    @Override
    public KbUpsertResponse kbUpsert(KbUpsertRequest request) {
        validator.validateKbUpsert(request);
        AiKbStoreDTO store = requireKnowledgeStore(request.getKbId(), true);
        request.setMeta(mergeStoreMeta(store, request.getMeta()));
        request.setKbId(store.getProviderKbId());
        return resolveKnowledgeService(requireConfiguredClientType()).kbUpsert(requestMapper.mapKbUpsert(request));
    }

    /**
     * 从已绑定的提供方 Dataset 删除文档。
     *
     * @param request 文档删除请求，使用本地知识库编码和待删除文档标识
     * @return 提供方删除结果
     */
    @Override
    public KbDeleteResponse kbDelete(KbDeleteRequest request) {
        validator.validateKbDelete(request);
        AiKbStoreDTO store = requireKnowledgeStore(request.getKbId(), false);
        request.setMeta(mergeStoreMeta(store, request.getMeta()));
        request.setKbId(store.getProviderKbId());
        return resolveKnowledgeService(requireConfiguredClientType()).kbDelete(requestMapper.mapKbDelete(request));
    }

    /**
     * 在本地知识库绑定的提供方 Dataset 中检索文档。
     *
     * @param request 检索请求，包含本地知识库编码、查询文本和召回参数
     * @return 检索结果，返回本地知识库编码而非提供方 Dataset 标识
     */
    @Override
    public KbSearchResponse kbSearch(KbSearchRequest request) {
        validator.validateKbSearch(request);
        String kbCode = resolveKbCode(request);
        AiKbStoreDTO store = requireKnowledgeStore(kbCode, true);
        request.setMeta(mergeStoreMeta(store, request.getMeta()));
        request.setKbId(store.getProviderKbId());
        KbSearchResponse response = resolveKnowledgeService(requireConfiguredClientType())
                .kbSearch(requestMapper.mapKbSearch(request));
        if (response == null) {
            response = new KbSearchResponse();
        }
        response.setKbCode(store.getKbCode());
        // 历史客户端读取 kbId；返回本地业务编码以避免泄露 Provider Dataset ID。
        response.setKbId(store.getKbCode());
        return response;
    }

    private String resolveKbCode(KbSearchRequest request) {
        if (StringUtils.hasText(request.getKbCode())) {
            return request.getKbCode().trim();
        }
        return request.getKbId().trim();
    }

    private AiKbStoreDTO requireKnowledgeStore(String kbCode) {
        return requireKnowledgeStore(kbCode, true);
    }

    private AiKbStoreDTO requireKnowledgeStore(String kbCode, boolean requireEnabled) {
        AiKbStoreDTO store = kbStoreService.getByKbCode(kbCode);
        if (store == null) {
            throw BizException.of(AiChatBizCodeConstant.KNOWLEDGE_SERVICE_NOT_FOUND, kbCode);
        }
        if (requireEnabled && !Boolean.TRUE.equals(store.getEnabled())) {
            throw BizException.of(AiChatBizCodeConstant.KNOWLEDGE_SERVICE_NOT_FOUND, kbCode);
        }
        if (!isSyncedStore(store) || !StringUtils.hasText(store.getProviderKbId())) {
            throw BizException.of(AiChatBizCodeConstant.KNOWLEDGE_SERVICE_NOT_FOUND, kbCode);
        }
        return store;
    }

    private boolean isSyncedStore(AiKbStoreDTO store) {
        return store.getSyncStatus() == null || store.getSyncStatus() == AiKbStoreSyncStatus.ACTIVE;
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
        ext.put("localKbCode", store.getKbCode());
        ext.put("providerKbId", store.getProviderKbId());
        merged.setExt(ext);
        merged = knowledgeClientConfigService.applySingle(merged);
        applyStoredAuth(store.getAuth(), merged);
        return merged;
    }

    /** 本地保留的认证快照优先于系统配置，用于凭据轮换前的稳定调用。 */
    private void applyStoredAuth(AiKbAuthConfig auth, RequestMeta target) {
        if (auth == null || auth.getType() == null) {
            return;
        }
        Map<String, Object> ext = target.getExt() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(target.getExt());
        if (auth.getType() == AiKbAuthType.BEARER && StringUtils.hasText(auth.getApiKey())) {
            ext.put("knowledgeClientAuth", Map.of("type", "bearer", "value", auth.getApiKey().trim()));
            ext.put("ragflowApiKey", auth.getApiKey().trim());
        }
        target.setExt(ext);
    }

    private AiKnowledgeClientType requireConfiguredClientType() {
        return knowledgeClientConfigService.requireSingleOption().getClientType();
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
