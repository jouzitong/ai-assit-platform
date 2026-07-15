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
import ai.platform.aiassit.service.ai.api.enums.AiKbStoreSyncStatus;
import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.data.jdbc.vo.PageResultVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@link AiKbStoreManageDomainService} 默认实现。 */
@Service
public class AiKbStoreManageDomainServiceImpl implements AiKbStoreManageDomainService {

    private static final String DEFAULT_CHUNK_METHOD = "one";
    private static final String PENDING_KB_CODE_PREFIX = "local-";
    private static final int MAX_SYNC_ERROR_LENGTH = 1024;

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
    public AiKbStoreVO add(AiKbStoreDTO dto) {
        AiKbStoreDTO target = merge(null, dto, true);
        applyDefaultChunkMethod(target);
        validate(target);
        target.setAuth(resolveConfiguredAuth());
        target.setKbCode(generatePendingKbCode());
        applySyncState(target, AiKbStoreSyncStatus.CREATING, null);
        AiKbStoreDTO created = storeService.add(target);
        return syncRemoteCreate(requireLocalStoreId(created), created);
    }

    @Override
    public AiKbStoreVO update(Long id, AiKbStoreDTO dto) {
        AiKbStoreDTO current = requireStore(id);
        AiKbStoreDTO target = merge(current, dto, true);
        validate(target);
        target.setAuth(resolveConfiguredAuth());
        if (shouldCreateRemoteOnSave(current)) {
            applySyncState(target, AiKbStoreSyncStatus.CREATING, null);
            AiKbStoreDTO pending = storeService.update(id, target);
            return syncRemoteCreate(id, pending == null ? target : pending);
        }
        requireProviderKbId(target);
        applySyncState(target, AiKbStoreSyncStatus.UPDATING, null);
        AiKbStoreDTO pending = storeService.update(id, target);
        return syncRemoteUpdate(id, pending == null ? target : pending);
    }

    @Override
    public AiKbStoreVO edit(Long id, AiKbStoreDTO dto) {
        AiKbStoreDTO current = requireStore(id);
        AiKbStoreDTO target = merge(current, dto, false);
        validate(target);
        target.setAuth(resolveConfiguredAuth());
        if (shouldCreateRemoteOnSave(current)) {
            applySyncState(target, AiKbStoreSyncStatus.CREATING, null);
            AiKbStoreDTO pending = storeService.edit(id, target);
            return syncRemoteCreate(id, pending == null ? target : pending);
        }
        requireProviderKbId(target);
        applySyncState(target, AiKbStoreSyncStatus.UPDATING, null);
        AiKbStoreDTO pending = storeService.edit(id, target);
        return syncRemoteUpdate(id, pending == null ? target : pending);
    }

    @Override
    public boolean delete(Long id) {
        AiKbStoreDTO store = requireStore(id);
        if (!StringUtils.hasText(store.getProviderKbId())) {
            return storeService.delete(id);
        }
        markStoreSyncState(id, AiKbStoreSyncStatus.DELETE_PENDING, null);
        return syncRemoteDelete(id, store);
    }

    @Override
    public boolean retrySync(Long id) {
        AiKbStoreDTO store = requireStore(id);
        AiKbStoreSyncStatus status = store.getSyncStatus();
        if (status == null || status == AiKbStoreSyncStatus.ACTIVE) {
            return true;
        }
        return switch (status) {
            case CREATING, CREATE_FAILED -> {
                AiKbStoreSyncStatus retryStatus = StringUtils.hasText(store.getProviderKbId())
                        ? AiKbStoreSyncStatus.UPDATING
                        : AiKbStoreSyncStatus.CREATING;
                applySyncState(store, retryStatus, null);
                AiKbStoreDTO pending = storeService.edit(id, store);
                if (StringUtils.hasText(store.getProviderKbId())) {
                    syncRemoteUpdate(id, pending == null ? store : pending);
                } else {
                    syncRemoteCreate(id, pending == null ? store : pending);
                }
                yield true;
            }
            case UPDATING, UPDATE_FAILED -> {
                requireProviderKbId(store);
                applySyncState(store, AiKbStoreSyncStatus.UPDATING, null);
                AiKbStoreDTO pending = storeService.edit(id, store);
                syncRemoteUpdate(id, pending == null ? store : pending);
                yield true;
            }
            case DELETE_PENDING, DELETE_FAILED -> {
                if (!StringUtils.hasText(store.getProviderKbId())) {
                    yield storeService.delete(id);
                }
                markStoreSyncState(id, AiKbStoreSyncStatus.DELETE_PENDING, null);
                yield syncRemoteDelete(id, store);
            }
            case ACTIVE -> true;
        };
    }

    private AiKbStoreVO syncRemoteCreate(Long id, AiKbStoreDTO target) {
        String createdProviderKbId = null;
        try {
            AiKbDatasetDTO dataset = datasetService.createDataset(requireRagflowClientKey(), toDatasetSaveRequest(target));
            createdProviderKbId = requireDatasetId(dataset);
            applyRemoteIdentifiers(target, createdProviderKbId);
            applySyncState(target, AiKbStoreSyncStatus.ACTIVE, null);
            AiKbStoreDTO active = storeService.edit(id, target);
            return toVO(active == null ? target : active);
        } catch (RuntimeException ex) {
            if (StringUtils.hasText(createdProviderKbId)) {
                boolean remoteDeleted = deleteRemoteDatasetQuietly(createdProviderKbId);
                if (!remoteDeleted) {
                    markStoreSyncStateQuietly(id, AiKbStoreSyncStatus.CREATE_FAILED, ex, createdProviderKbId);
                    throw ex;
                }
            }
            markStoreSyncStateQuietly(id, AiKbStoreSyncStatus.CREATE_FAILED, ex);
            throw ex;
        }
    }

    private boolean shouldCreateRemoteOnSave(AiKbStoreDTO current) {
        if (StringUtils.hasText(current.getProviderKbId())) {
            return false;
        }
        AiKbStoreSyncStatus status = current.getSyncStatus();
        return status == AiKbStoreSyncStatus.CREATING || status == AiKbStoreSyncStatus.CREATE_FAILED;
    }

    private AiKbStoreVO syncRemoteUpdate(Long id, AiKbStoreDTO target) {
        try {
            AiKbDatasetDTO dataset = datasetService.updateDataset(requireRagflowClientKey(), target.getProviderKbId(), toDatasetSaveRequest(target));
            applyRemoteIdentifiers(target, requireDatasetId(dataset));
            applySyncState(target, AiKbStoreSyncStatus.ACTIVE, null);
            AiKbStoreDTO active = storeService.edit(id, target);
            return toVO(active == null ? target : active);
        } catch (RuntimeException ex) {
            markStoreSyncStateQuietly(id, AiKbStoreSyncStatus.UPDATE_FAILED, ex);
            throw ex;
        }
    }

    private boolean syncRemoteDelete(Long id, AiKbStoreDTO store) {
        try {
            deleteRemoteDataset(store.getProviderKbId());
            return storeService.delete(id);
        } catch (RuntimeException ex) {
            markStoreSyncStateQuietly(id, AiKbStoreSyncStatus.DELETE_FAILED, ex);
            throw ex;
        }
    }

    private void markStoreSyncState(Long id, AiKbStoreSyncStatus status, Throwable error) {
        AiKbStoreDTO patch = new AiKbStoreDTO();
        applySyncState(patch, status, error == null ? null : normalizeErrorMessage(error));
        storeService.edit(id, patch);
    }

    private void markStoreSyncStateQuietly(Long id, AiKbStoreSyncStatus status, Throwable error) {
        try {
            markStoreSyncState(id, status, error);
        } catch (RuntimeException ignored) {
            // Preserve the original RAGFlow/local sync failure for the caller.
        }
    }

    private void markStoreSyncStateQuietly(Long id, AiKbStoreSyncStatus status, Throwable error, String providerKbId) {
        try {
            AiKbStoreDTO patch = new AiKbStoreDTO();
            applyRemoteIdentifiers(patch, providerKbId);
            applySyncState(patch, status, normalizeErrorMessage(error));
            storeService.edit(id, patch);
        } catch (RuntimeException ignored) {
            // Preserve the original RAGFlow/local sync failure for the caller.
        }
    }

    private void applySyncState(AiKbStoreDTO target, AiKbStoreSyncStatus status, String error) {
        target.setSyncStatus(status);
        target.setSyncError(truncateSyncError(error));
        target.setLastSyncAt(LocalDateTime.now());
    }

    private Long requireLocalStoreId(AiKbStoreDTO store) {
        if (store == null || store.getId() == null) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED, "Local KB store id is empty");
        }
        return store.getId();
    }

    private String requireDatasetId(AiKbDatasetDTO dataset) {
        String providerKbId = dataset == null ? null : trimToNull(dataset.getKbId());
        if (!StringUtils.hasText(providerKbId)) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_RESPONSE_INVALID, "RAGFlow dataset id is empty");
        }
        return providerKbId;
    }

    private String generatePendingKbCode() {
        return PENDING_KB_CODE_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    private String normalizeErrorMessage(Throwable error) {
        String message = error.getMessage();
        if (!StringUtils.hasText(message) && error.getCause() != null) {
            message = error.getCause().getMessage();
        }
        return StringUtils.hasText(message) ? message : error.getClass().getSimpleName();
    }

    private String truncateSyncError(String message) {
        if (!StringUtils.hasText(message) || message.length() <= MAX_SYNC_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_SYNC_ERROR_LENGTH);
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
        target.setSyncStatus(current == null ? source.getSyncStatus() : current.getSyncStatus());
        target.setSyncError(current == null ? source.getSyncError() : current.getSyncError());
        target.setLastSyncAt(current == null ? source.getLastSyncAt() : current.getLastSyncAt());
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
        if (!isValidRagflowEmbeddingModel(dto.getEmbeddingModel())) {
            throw BizException.of(AiChatBizCodeConstant.REQUIRED_EMBEDDING_MODEL,
                    "Embedding model must be RAGFlow model_id or model@provider");
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

    private boolean isValidRagflowEmbeddingModel(String embeddingModel) {
        String normalized = trimToNull(embeddingModel);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        if (normalized.matches("^[0-9a-fA-F]{32}$")) {
            return true;
        }
        String[] parts = normalized.split("@");
        if (parts.length < 2) {
            return false;
        }
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                return false;
            }
        }
        return true;
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

    private boolean deleteRemoteDatasetQuietly(String providerKbId) {
        try {
            deleteRemoteDataset(providerKbId);
            return true;
        } catch (RuntimeException ignored) {
            // Keep the original local persistence failure visible to the caller.
            return false;
        }
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
