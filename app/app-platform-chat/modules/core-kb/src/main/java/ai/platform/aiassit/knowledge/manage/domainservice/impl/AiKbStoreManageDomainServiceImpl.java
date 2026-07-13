package ai.platform.aiassit.knowledge.manage.domainservice.impl;

import ai.platform.aiassit.execution.service.KnowledgeClientConfigService;
import ai.platform.aiassit.execution.service.KnowledgeClientOption;
import ai.platform.aiassit.knowledge.manage.domainservice.AiKbStoreManageDomainService;
import ai.platform.aiassit.knowledge.manage.domainservice.AiKnowledgeDatasetService;
import ai.platform.aiassit.knowledge.manage.entity.store.dto.AiKbStoreDTO;
import ai.platform.aiassit.knowledge.manage.entity.store.req.AiKbStoreQueryRequest;
import ai.platform.aiassit.knowledge.manage.service.AiKbStoreService;
import ai.platform.aiassit.knowledge.manage.vo.AiKbAuthVO;
import ai.platform.aiassit.knowledge.manage.vo.AiKbStoreVO;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.dto.AiKbAuthConfig;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDeleteRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetSaveRequest;
import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.data.jdbc.vo.PageResultVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link AiKbStoreManageDomainService} 默认实现。 */
@Service
public class AiKbStoreManageDomainServiceImpl implements AiKbStoreManageDomainService {

    private static final String DEFAULT_CHUNK_METHOD = "one";

    private final AiKbStoreService storeService;
    private final KnowledgeClientConfigService knowledgeClientConfigService;
    private final AiKnowledgeDatasetService datasetService;

    public AiKbStoreManageDomainServiceImpl(AiKbStoreService storeService,
                                            KnowledgeClientConfigService knowledgeClientConfigService,
                                            AiKnowledgeDatasetService datasetService) {
        this.storeService = storeService;
        this.knowledgeClientConfigService = knowledgeClientConfigService;
        this.datasetService = datasetService;
    }

    @Override
    public PageResultVO<AiKbStoreVO> page(AiKbStoreQueryRequest request) {
        PageResultVO<AiKbStoreDTO> result = storeService.page(request == null ? new AiKbStoreQueryRequest() : request);
        List<AiKbStoreVO> list = result.getList().stream().map(this::toVO).toList();
        return PageResultVO.of(list, result.getPageInfo());
    }

    @Override
    public AiKbStoreVO get(Long id) {
        return toVO(requireStore(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKbStoreVO add(AiKbStoreDTO dto) {
        AiKbStoreDTO target = merge(null, dto, true);
        applyDefaultChunkMethod(target);
        validate(target);
        target.setAuth(resolveConfiguredAuth());
        AiKbDatasetDTO dataset = datasetService.createDataset(requireRagflowClientKey(), toDatasetSaveRequest(target));
        if (!StringUtils.hasText(dataset.getKbId())) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_RESPONSE_INVALID, "RAGFlow dataset id is empty");
        }
        applyRemoteIdentifiers(target, dataset.getKbId());
        try {
            return toVO(storeService.add(target));
        } catch (RuntimeException ex) {
            deleteRemoteDataset(dataset.getKbId());
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKbStoreVO update(Long id, AiKbStoreDTO dto) {
        AiKbStoreDTO target = merge(requireStore(id), dto, true);
        validate(target);
        target.setAuth(resolveConfiguredAuth());
        requireProviderKbId(target);
        AiKbDatasetDTO dataset = datasetService.updateDataset(requireRagflowClientKey(), target.getProviderKbId(), toDatasetSaveRequest(target));
        applyRemoteIdentifiers(target, dataset.getKbId());
        return toVO(storeService.update(id, target));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKbStoreVO edit(Long id, AiKbStoreDTO dto) {
        AiKbStoreDTO current = requireStore(id);
        AiKbStoreDTO target = merge(current, dto, false);
        validate(target);
        target.setAuth(resolveConfiguredAuth());
        requireProviderKbId(target);
        AiKbDatasetDTO dataset = datasetService.updateDataset(requireRagflowClientKey(), target.getProviderKbId(), toDatasetSaveRequest(target));
        applyRemoteIdentifiers(target, dataset.getKbId());
        return toVO(storeService.edit(id, target));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        AiKbStoreDTO store = requireStore(id);
        requireProviderKbId(store);
        deleteRemoteDataset(store.getProviderKbId());
        return storeService.delete(id);
    }

    private AiKbStoreDTO merge(AiKbStoreDTO current, AiKbStoreDTO incoming, boolean replaceNulls) {
        AiKbStoreDTO source = incoming == null ? new AiKbStoreDTO() : incoming;
        AiKbStoreDTO target = new AiKbStoreDTO();
        target.setId(current == null ? source.getId() : current.getId());
        // 知识库编码与 Provider Dataset ID 均由 RAGFlow 分配，页面请求不可覆盖。
        target.setKbCode(current == null ? null : current.getKbCode());
        target.setKbName(choose(trimToNull(source.getKbName()), current == null ? null : current.getKbName(), replaceNulls));
        target.setProviderKbId(current == null ? null : current.getProviderKbId());
        target.setDescription(choose(trimToNull(source.getDescription()), current == null ? null : current.getDescription(), replaceNulls));
        target.setEmbeddingModel(choose(trimToNull(source.getEmbeddingModel()), current == null ? null : current.getEmbeddingModel(), replaceNulls));
        target.setPermission(choose(trimToNull(source.getPermission()), current == null ? null : current.getPermission(), replaceNulls));
        target.setChunkMethod(choose(trimToNull(source.getChunkMethod()), current == null ? null : current.getChunkMethod(), replaceNulls));
        target.setParserConfig(choose(copyMap(source.getParserConfig()), current == null ? null : copyMap(current.getParserConfig()), replaceNulls));
        target.setParseType(choose(trimToNull(source.getParseType()), current == null ? null : current.getParseType(), replaceNulls));
        target.setPipelineId(choose(trimToNull(source.getPipelineId()), current == null ? null : current.getPipelineId(), replaceNulls));
        target.setEnabled(choose(source.getEnabled(), current == null ? null : current.getEnabled(), replaceNulls));
        target.setTags(choose(copyList(source.getTags()), current == null ? null : copyList(current.getTags()), replaceNulls));
        target.setAuth(copyAuth(current == null ? null : current.getAuth()));
        target.setExtJson(choose(copyMap(source.getExtJson()), current == null ? null : copyMap(current.getExtJson()), replaceNulls));
        return target;
    }

    private void validate(AiKbStoreDTO dto) {
        if (!StringUtils.hasText(dto.getKbName())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_MESSAGE);
        }
        if (!StringUtils.hasText(dto.getEmbeddingModel())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_EMBEDDING_MODEL);
        }
        if (!StringUtils.hasText(dto.getChunkMethod()) && !StringUtils.hasText(dto.getPipelineId())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_CHUNK_METHOD);
        }
        if (StringUtils.hasText(dto.getPipelineId())
                && (StringUtils.hasText(dto.getChunkMethod()) || (dto.getParserConfig() != null && !dto.getParserConfig().isEmpty()))) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED,
                    "pipelineId cannot be combined with chunkMethod or parserConfig");
        }
    }

    private AiKbStoreDTO requireStore(Long id) {
        AiKbStoreDTO store = storeService.get(id);
        if (store == null) {
            throw BizException.of(AiChatBizCodeConstant.KNOWLEDGE_SERVICE_NOT_FOUND, id);
        }
        return store;
    }

    private AiKbStoreVO toVO(AiKbStoreDTO source) {
        AiKbStoreVO target = new AiKbStoreVO();
        BeanUtils.copyProperties(source, target, "auth");
        target.setAuth(toAuthVO(source.getAuth()));
        return target;
    }

    private String requireRagflowClientKey() {
        KnowledgeClientOption option = knowledgeClientConfigService.requireSingleOption();
        if (option.getClientType() != AiKnowledgeClientType.RAGFLOW) {
            throw BizException.of(AiChatBizCodeConstant.KNOWLEDGE_SERVICE_NOT_FOUND,
                    "single knowledge client must be RAGFLOW");
        }
        return option.getKey();
    }

    private AiKbAuthConfig resolveConfiguredAuth() {
        return copyAuth(knowledgeClientConfigService.resolveAuth(requireRagflowClientKey()));
    }

    private AiKbAuthConfig copyAuth(AiKbAuthConfig source) {
        if (source == null) {
            return null;
        }
        AiKbAuthConfig target = new AiKbAuthConfig();
        target.setType(source.getType());
        target.setApiKey(source.getApiKey());
        target.setAccessKeyId(source.getAccessKeyId());
        target.setAccessKeySecret(source.getAccessKeySecret());
        return target;
    }

    private AiKbAuthVO toAuthVO(AiKbAuthConfig source) {
        if (source == null) {
            return null;
        }
        AiKbAuthVO target = new AiKbAuthVO();
        target.setType(source.getType());
        target.setApiKeyMasked(mask(source.getApiKey()));
        target.setAccessKeyIdMasked(mask(source.getAccessKeyId()));
        target.setAccessKeySecretMasked(mask(source.getAccessKeySecret()));
        return target;
    }

    private String mask(String value) {
        if (!StringUtils.hasText(value) || value.length() <= 12) {
            return StringUtils.hasText(value) ? "****" : null;
        }
        return value.substring(0, 8) + "****" + value.substring(value.length() - 4);
    }

    private AiKbDatasetSaveRequest toDatasetSaveRequest(AiKbStoreDTO store) {
        AiKbDatasetSaveRequest request = new AiKbDatasetSaveRequest();
        request.setName(store.getKbName());
        request.setDescription(store.getDescription());
        request.setEmbeddingModel(store.getEmbeddingModel());
        request.setPermission(store.getPermission());
        request.setChunkMethod(store.getChunkMethod());
        request.setParserConfig(copyMap(store.getParserConfig()));
        request.setParseType(store.getParseType());
        request.setPipelineId(store.getPipelineId());
        return request;
    }

    private void deleteRemoteDataset(String providerKbId) {
        AiKbDatasetDeleteRequest request = new AiKbDatasetDeleteRequest();
        request.setKbIds(List.of(providerKbId));
        datasetService.deleteDatasets(requireRagflowClientKey(), request);
    }

    private void requireProviderKbId(AiKbStoreDTO store) {
        if (!StringUtils.hasText(store.getProviderKbId())) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_RESPONSE_INVALID, "RAGFlow dataset id is empty");
        }
    }

    private void applyDefaultChunkMethod(AiKbStoreDTO target) {
        if (!StringUtils.hasText(target.getChunkMethod()) && !StringUtils.hasText(target.getPipelineId())) {
            target.setChunkMethod(DEFAULT_CHUNK_METHOD);
        }
    }

    private void applyRemoteIdentifiers(AiKbStoreDTO target, String providerKbId) {
        String remoteId = trimToNull(providerKbId);
        if (!StringUtils.hasText(remoteId)) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_RESPONSE_INVALID, "RAGFlow dataset id is empty");
        }
        target.setProviderKbId(remoteId);
        target.setKbCode(remoteId);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? null : new LinkedHashMap<>(source);
    }

    private List<String> copyList(List<String> source) {
        return source == null ? null : List.copyOf(source);
    }

    private <T> T choose(T incoming, T current, boolean replaceNulls) {
        return replaceNulls ? incoming : incoming != null ? incoming : current;
    }
}
