package ai.platform.aiassit.knowledge.manage.domainservice.impl;

import ai.platform.aiassit.service.ai.api.constant.AiKbBizCodeConstant;
import ai.platform.aiassit.service.ai.api.dto.AiKbCreateRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentContentUpdateRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentDetailDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentListItemDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentListRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassit.service.ai.api.dto.AiKbInfoDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbListRequest;
import ai.platform.aiassit.service.ai.api.dto.KbDocument;
import ai.platform.aiassit.service.ai.api.dto.KbUpsertRequest;
import ai.platform.aiassit.service.ai.api.dto.KbUpsertResponse;
import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.api.enums.AiKbBizType;
import ai.platform.aiassit.service.ai.api.enums.AiKbChangeType;
import ai.platform.aiassit.service.ai.api.enums.AiKbContentFormat;
import ai.platform.aiassit.service.ai.api.enums.AiKbDocumentStatus;
import ai.platform.aiassit.service.ai.api.enums.AiKbDocumentType;
import ai.platform.aiassit.service.ai.api.enums.AiKbProviderSyncStatus;
import ai.platform.aiassit.execution.service.AiKnowledgeExecutionService;
import ai.platform.aiassit.knowledge.manage.req.AiKbDeleteRequest;
import ai.platform.aiassit.knowledge.manage.req.AiKbSyncCheckRequest;
import ai.platform.aiassit.knowledge.manage.req.AiKbSyncRequest;
import ai.platform.aiassit.knowledge.manage.resp.AiKbDeleteResponse;
import ai.platform.aiassit.knowledge.manage.resp.AiKbSyncCheckResponse;
import ai.platform.aiassit.knowledge.manage.resp.AiKbSyncResponse;
import ai.platform.aiassit.knowledge.manage.domainservice.AiKnowledgeManageDomainService;
import ai.platform.aiassit.knowledge.manage.entity.dto.AiKbDocumentContentDTO;
import ai.platform.aiassit.knowledge.manage.entity.dto.AiKbDocumentDTO;
import ai.platform.aiassit.knowledge.manage.entity.dto.AiKbDocumentVersionContentDTO;
import ai.platform.aiassit.knowledge.manage.entity.dto.AiKbDocumentVersionDTO;
import ai.platform.aiassit.knowledge.manage.entity.dto.AiKbStoreDTO;
import ai.platform.aiassit.knowledge.manage.entity.req.AiKbDocumentQueryRequest;
import ai.platform.aiassit.knowledge.manage.entity.req.AiKbDocumentVersionContentQueryRequest;
import ai.platform.aiassit.knowledge.manage.entity.req.AiKbDocumentVersionQueryRequest;
import ai.platform.aiassit.knowledge.manage.service.AiKbDocumentContentService;
import ai.platform.aiassit.knowledge.manage.service.AiKbDocumentService;
import ai.platform.aiassit.knowledge.manage.service.AiKbDocumentVersionContentService;
import ai.platform.aiassit.knowledge.manage.service.AiKbDocumentVersionService;
import ai.platform.aiassit.knowledge.manage.service.AiKbStoreService;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.data.jdbc.vo.PageInfo;
import org.athena.framework.data.jdbc.vo.PageResultVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AiKnowledgeManageDomainServiceImpl implements AiKnowledgeManageDomainService {

    private static final String LAST_SYNC_CHECKSUM_KEY = "lastSyncChecksum";
    private static final String LAST_SYNC_VERSION_NO_KEY = "lastSyncVersionNo";
    private static final String LAST_SYNC_AT_KEY = "lastSyncAt";
    private static final String LAST_SYNC_PROVIDER_DOCUMENT_ID_KEY = "lastSyncProviderDocumentId";

    private final AiKbStoreService storeService;
    private final AiKbDocumentService documentService;
    private final AiKbDocumentContentService contentService;
    private final AiKbDocumentVersionService documentVersionService;
    private final AiKbDocumentVersionContentService documentVersionContentService;
    private final AiKnowledgeExecutionService aiKnowledgeExecutionService;

    public AiKnowledgeManageDomainServiceImpl(AiKbStoreService storeService,
                                                  AiKbDocumentService documentService,
                                                  AiKbDocumentContentService contentService,
                                                  AiKbDocumentVersionService documentVersionService,
                                                  AiKbDocumentVersionContentService documentVersionContentService,
                                                  AiKnowledgeExecutionService aiKnowledgeExecutionService) {
        this.storeService = storeService;
        this.documentService = documentService;
        this.contentService = contentService;
        this.documentVersionService = documentVersionService;
        this.documentVersionContentService = documentVersionContentService;
        this.aiKnowledgeExecutionService = aiKnowledgeExecutionService;
    }

    @Override
    public List<AiKbInfoDTO> kbList(AiKbListRequest request) {
        List<AiKbStoreDTO> stores = storeService.list(request);
        List<AiKbInfoDTO> result = new ArrayList<>(stores.size());
        for (AiKbStoreDTO store : stores) {
            result.add(toKbInfo(store));
        }
        log.info("ai kb list finish, enabled={}, resultSize={}",
                request == null ? null : request.getEnabled(),
                result.size());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKbInfoDTO createKnowledgeBase(AiKbCreateRequest request) {
        validateCreateRequest(request);
        String kbCode = request.getKbCode().trim();
        AiKbStoreDTO existing = storeService.getByKbCode(kbCode);
        if (existing != null) {
            throw BizException.of(AiKbBizCodeConstant.KB_STORE_EXISTS, kbCode);
        }

        Map<String, Object> ext = normalizeExt(request.getExt());

        AiKbStoreDTO store = new AiKbStoreDTO();
        store.setKbCode(kbCode);
        store.setKbName(request.getKbName().trim());
        store.setProviderKbId(trimToNull(request.getProviderKbId()));
        store.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        store.setTags(normalizeTags(request.getTags()));
        store.setUrl(trimToNull(request.getUrl()));
        store.setExtJson(ext);
        store = storeService.add(store);
        log.info("ai kb store created, kbCode={}, kbName={}, enabled={}",
                store.getKbCode(), store.getKbName(), store.getEnabled());
        return toKbInfo(store);
    }

    @Override
    public String getKbId(AiKbListRequest request) {
        List<AiKbInfoDTO> list = kbList(request);
        return list.isEmpty() ? null : list.get(0).getKbId();
    }

    @Override
    public PageResultVO<AiKbDocumentListItemDTO> listDocuments(AiKbDocumentListRequest request) {
        AiKbDocumentQueryRequest query = new AiKbDocumentQueryRequest();
        int page = 1;
        int size = 10;
        if (request != null) {
            query.setKbCode(trimToNull(request.getKbCode()));
            query.setDocumentCode(trimToNull(request.getDocumentCode()));
            query.setKeyword(trimToNull(request.getKeyword()));
            query.setBizType(resolveBizType(request.getBizTypeCode()));
            query.setStatus(resolveDocumentStatus(request.getTab()));
            page = safePage(request.getPage());
            size = safeSize(request.getSize());
        } else {
            query.setStatus(AiKbDocumentStatus.ACTIVE);
        }
        query.setPage(page);
        query.setSize(size);
        PageResultVO<AiKbDocumentDTO> result = documentService.page(query);
        List<AiKbDocumentListItemDTO> records = result.getList().stream()
                .map(this::toDocumentListItem)
                .toList();
        return PageResultVO.of(records, new PageInfo(result.getPageInfo().total(), size, page));
    }

    @Override
    public AiKbDocumentDetailDTO getDocumentDetail(String kbCode, String documentCode) {
        if (!StringUtils.hasText(kbCode) || !StringUtils.hasText(documentCode)) {
            throw BizException.illegalParam(AiKbBizCodeConstant.REQUIRED_KB_CODE_AND_DOCUMENT_CODE);
        }
        AiKbDocumentDTO document = documentService.getByKbCodeAndDocumentCode(kbCode.trim(), documentCode.trim());
        if (document == null) {
            throw BizException.of(AiKbBizCodeConstant.DOCUMENT_NOT_FOUND, kbCode, documentCode);
        }
        AiKbDocumentContentDTO content = contentService.getByDocumentId(document.getId());
        return toDocumentDetail(document, content);
    }

    /**
     * 新增或覆盖更新本地知识库当前文档。
     *
     * <p>更新已有文档时，先把覆盖前的文档和正文写入历史版本快照，再更新当前文档。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKbDocumentUpsertResponse upsertDocument(AiKbDocumentUpsertRequest request) {
        validateUpsertRequest(request);
        String kbId = request.getKbId().trim();
        AiKbStoreDTO store = requireStore(kbId);
        AiKbDocumentType documentType = resolveDocumentType(request);
        String documentId = request.getDocumentId().trim();
        String documentName = StringUtils.hasText(request.getDocumentName()) ? request.getDocumentName().trim() : documentId;
        Map<String, Object> ext = normalizeExt(request.getExt());
        AiKbBizType bizType = resolveBizType(documentType, request.getBizType());
        AiKbDocumentDTO existing = documentService.getByKbCodeAndDocumentCode(kbId, documentId);
        boolean created = existing == null;
        String sourceKey = resolveUpsertSourceKey(ext, existing);
        if (StringUtils.hasText(sourceKey)) {
            ext.put("sourceKey", sourceKey);
        }
        String documentBizKey = documentId;
        String checksum = checksum(request.getContent());
        long contentSize = request.getContent().getBytes(StandardCharsets.UTF_8).length;
        boolean canUpdate = Boolean.TRUE.equals(request.getCanUpdate());
        log.info("ai kb upsert document start, kbId={}, documentId={}, documentType={}, bizType={}, sourceKey={}, canUpdate={}",
                kbId, documentId, documentType, bizType, sourceKey, canUpdate);

        if (existing != null && !canUpdate) {
            log.info("ai kb document exists and update skipped, kbId={}, documentId={}, currentVersionNo={}",
                    kbId, documentId, existing.getDocumentVersionNo());
            return buildUpsertResponse(kbId, documentId, false, false, true, false,
                    existing.getDocumentVersionNo(), null, "document exists and canUpdate is false");
        }

        boolean updated = false;
        AiKbDocumentDTO document = created ? new AiKbDocumentDTO() : existing;
        if (created) {
            document.setKbCode(kbId);
            document.setDocumentCode(documentId);
            document.setDocumentVersionNo(1);
            document.setStatus(AiKbDocumentStatus.ACTIVE);
        }

        boolean contentChanged = created || !Objects.equals(existing.getContentChecksum(), checksum);
        boolean metadataChanged = created
                || !Objects.equals(document.getDocumentName(), documentName)
                || !Objects.equals(document.getDocumentType(), documentType)
                || !Objects.equals(document.getBizType(), bizType)
                || !Objects.equals(document.getBizKey(), documentBizKey)
                || !Objects.equals(document.getMetaJson(), ext);
        log.info("ai kb upsert document diff, kbId={}, documentId={}, created={}, contentChanged={}, metadataChanged={}",
                kbId, documentId, created, contentChanged, metadataChanged);

        Integer previousVersionNo = created ? null : document.getDocumentVersionNo();
        if (!created && (contentChanged || metadataChanged)) {
            AiKbDocumentContentDTO oldContent = contentService.getByDocumentId(document.getId());
            saveDocumentSnapshot(document, oldContent, AiKbChangeType.UPDATE, LocalDateTime.now());
            document.setDocumentVersionNo((document.getDocumentVersionNo() == null ? 0 : document.getDocumentVersionNo()) + 1);
            updated = true;
        }

        document.setDocumentName(documentName);
        document.setDocumentType(documentType);
        document.setBizType(bizType);
        document.setBizKey(documentBizKey);
        document.setContentChecksum(checksum);
        document.setContentFormat(AiKbContentFormat.MARKDOWN);
        document.setContentSize(contentSize);
        document.setMetaJson(ext);
        document.setLastGeneratedAt(LocalDateTime.now());
        document.setLastError(null);
        if (created || updated) {
            document.setProviderSyncStatus(AiKbProviderSyncStatus.PENDING);
        }
        if (Boolean.FALSE.equals(store.getEnabled())) {
            document.setStatus(AiKbDocumentStatus.DISABLED);
        }

        if (created) {
            document = documentService.add(document);
            log.info("ai kb document created, kbId={}, documentId={}, currentVersionNo={}",
                    kbId, documentId, document.getDocumentVersionNo());
        } else if (updated) {
            document = documentService.update(document.getId(), document);
            log.info("ai kb document updated, kbId={}, documentId={}, currentVersionNo={}",
                    kbId, documentId, document.getDocumentVersionNo());
        } else {
            log.info("ai kb document unchanged, kbId={}, documentId={}, currentVersionNo={}",
                    kbId, documentId, document.getDocumentVersionNo());
        }

        AiKbDocumentContentDTO content = contentService.getByDocumentId(document.getId());
        if (content == null) {
            content = new AiKbDocumentContentDTO();
            content.setDocumentId(document.getId());
            content.setContentFormat(AiKbContentFormat.MARKDOWN);
            content.setContentSize(contentSize);
            content.setContentJson(null);
            content.setRenderedContent(request.getContent());
            content.setExtJson(ext);
            contentService.add(content);
            log.info("ai kb document content created, kbId={}, documentId={}, contentSize={}",
                    kbId, documentId, contentSize);
        } else if (updated || !Objects.equals(content.getRenderedContent(), request.getContent())) {
            content.setContentFormat(AiKbContentFormat.MARKDOWN);
            content.setContentSize(contentSize);
            content.setRenderedContent(request.getContent());
            content.setExtJson(ext);
            contentService.update(content.getId(), content);
            log.info("ai kb document content updated, kbId={}, documentId={}, contentSize={}",
                    kbId, documentId, contentSize);
        }

        boolean unchanged = !created && !updated;
        log.info("ai kb upsert document finish, kbId={}, documentId={}, created={}, updated={}, currentVersionNo={}",
                kbId, documentId, created, updated, document.getDocumentVersionNo());
        return buildUpsertResponse(kbId, documentId, created, updated, !created, unchanged,
                document.getDocumentVersionNo(), updated ? previousVersionNo : null,
                unchanged ? "document unchanged" : "document upserted");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKbDocumentUpsertResponse updateDocumentContent(AiKbDocumentContentUpdateRequest request) {
        validateContentUpdateRequest(request);
        AiKbDocumentDTO document = getDocumentById(request.getDocumentId());
        if (document == null) {
            throw BizException.of(AiKbBizCodeConstant.DOCUMENT_NOT_FOUND, request.getDocumentId());
        }

        String newContent = request.getContent();
        String newChecksum = checksum(newContent);
        long newContentSize = newContent.getBytes(StandardCharsets.UTF_8).length;
        AiKbDocumentContentDTO content = contentService.getByDocumentId(document.getId());
        boolean contentChanged = !Objects.equals(document.getContentChecksum(), newChecksum)
                || content == null
                || !Objects.equals(content.getRenderedContent(), newContent);
        if (!contentChanged) {
            return buildUpsertResponse(document.getKbCode(), document.getDocumentCode(),
                    false, false, true, true, document.getDocumentVersionNo(), null,
                    "document content unchanged");
        }

        Integer previousVersionNo = document.getDocumentVersionNo();
        saveDocumentSnapshot(document, content, AiKbChangeType.UPDATE, LocalDateTime.now());
        document.setDocumentVersionNo((document.getDocumentVersionNo() == null ? 0 : document.getDocumentVersionNo()) + 1);
        document.setContentChecksum(newChecksum);
        document.setContentFormat(AiKbContentFormat.MARKDOWN);
        document.setContentSize(newContentSize);
        document.setProviderSyncStatus(AiKbProviderSyncStatus.PENDING);
        document.setLastGeneratedAt(LocalDateTime.now());
        document.setLastError(null);
        document = documentService.update(document.getId(), document);

        Map<String, Object> ext = request.getExt() == null ? null : normalizeExt(request.getExt());
        if (content == null) {
            content = new AiKbDocumentContentDTO();
            content.setDocumentId(document.getId());
            content.setContentFormat(AiKbContentFormat.MARKDOWN);
            content.setContentSize(newContentSize);
            content.setContentJson(null);
            content.setRenderedContent(newContent);
            content.setExtJson(ext == null ? new LinkedHashMap<>() : ext);
            contentService.add(content);
        } else {
            content.setContentFormat(AiKbContentFormat.MARKDOWN);
            content.setContentSize(newContentSize);
            content.setRenderedContent(newContent);
            if (ext != null) {
                content.setExtJson(ext);
            }
            contentService.update(content.getId(), content);
        }

        log.info("ai kb document content updated by id, documentId={}, kbCode={}, documentCode={}, currentVersionNo={}",
                document.getId(), document.getKbCode(), document.getDocumentCode(), document.getDocumentVersionNo());
        return buildUpsertResponse(document.getKbCode(), document.getDocumentCode(),
                false, true, true, false, document.getDocumentVersionNo(), previousVersionNo,
                "document content updated");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKbSyncResponse syncDocument(AiKbSyncRequest request) {
        if (request == null || !StringUtils.hasText(request.getKbCode())) {
            throw BizException.illegalParam(AiKbBizCodeConstant.REQUIRED_KB_CODE);
        }
        AiKbDocumentQueryRequest query = new AiKbDocumentQueryRequest();
        query.setKbCode(trimToNull(request.getKbCode()));
        query.setStatus(AiKbDocumentStatus.ACTIVE);
        query.setPage(1);
        query.setSize(Integer.MAX_VALUE);
        List<AiKbDocumentDTO> documents = documentService.listByQuery(query);

        Set<String> targetDocumentCodes = request.getDocumentCodes() == null
                ? Set.of()
                : request.getDocumentCodes().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(HashSet::new));
        if (!targetDocumentCodes.isEmpty()) {
            documents = documents.stream()
                    .filter(item -> targetDocumentCodes.contains(item.getDocumentCode()))
                    .toList();
        } else if (!Boolean.TRUE.equals(request.getForce())) {
            documents = documents.stream()
                    .filter(this::shouldSyncDocument)
                    .toList();
        }
        if (documents.isEmpty()) {
            throw BizException.of(AiKbBizCodeConstant.CURRENT_DOCUMENT_NOT_FOUND);
        }

        AiKbSyncResponse response = new AiKbSyncResponse();
        response.setAcceptedCount(0);
        Map<String, List<AiKbDocumentDTO>> documentsByKb = documents.stream()
                .collect(Collectors.groupingBy(AiKbDocumentDTO::getKbCode, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<AiKbDocumentDTO>> entry : documentsByKb.entrySet()) {
            try {
                PublishResult result = syncCurrentDocuments(entry.getKey(), entry.getValue());
                response.setAcceptedCount(response.getAcceptedCount() + result.acceptedCount());
                response.getSkippedDocumentCodes().addAll(result.skippedDocumentCodes());
            } catch (RuntimeException ex) {
                log.warn("ai kb sync current documents failed, kbCode={}, documentCount={}",
                        entry.getKey(), entry.getValue().size(), ex);
                response.getSkippedDocumentCodes().addAll(entry.getValue().stream()
                        .map(AiKbDocumentDTO::getDocumentCode)
                        .filter(StringUtils::hasText)
                        .toList());
            }
        }
        return response;
    }

    @Override
    public AiKbSyncCheckResponse checkDocumentSync(AiKbSyncCheckRequest request) {
        AiKbDocumentQueryRequest query = new AiKbDocumentQueryRequest();
        query.setKbCode(request == null ? null : trimToNull(request.getKbCode()));
        query.setPage(1);
        query.setSize(Integer.MAX_VALUE);
        List<AiKbDocumentDTO> documents = documentService.listByQuery(query);

        Set<String> targetDocumentCodes = request == null || request.getDocumentCodes() == null
                ? Set.of()
                : request.getDocumentCodes().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(HashSet::new));
        if (!targetDocumentCodes.isEmpty()) {
            documents = documents.stream()
                    .filter(item -> targetDocumentCodes.contains(item.getDocumentCode()))
                    .toList();
        }
        if (documents.isEmpty()) {
            throw BizException.of(AiKbBizCodeConstant.CURRENT_DOCUMENT_NOT_FOUND);
        }

        AiKbSyncCheckResponse response = new AiKbSyncCheckResponse();
        response.setTotalCount(documents.size());
        for (AiKbDocumentDTO document : documents) {
            AiKbDocumentContentDTO content = contentService.getByDocumentId(document.getId());
            AiKbSyncCheckResponse.Item item = buildSyncCheckItem(document, content);
            response.getItems().add(item);
            switch (item.getStatus()) {
                case "MATCHED" -> response.setMatchedCount(response.getMatchedCount() + 1);
                case "CHANGED" -> response.setChangedCount(response.getChangedCount() + 1);
                case "NOT_SYNCED" -> response.setNotSyncedCount(response.getNotSyncedCount() + 1);
                default -> response.setMissingSnapshotCount(response.getMissingSnapshotCount() + 1);
            }
        }
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKbDeleteResponse deleteDocument(AiKbDeleteRequest request) {
        if (request == null || request.getDocumentCodes() == null || request.getDocumentCodes().isEmpty()) {
            throw BizException.illegalParam(AiKbBizCodeConstant.REQUIRED_DTO);
        }
        String requestKbCode = trimToNull(request.getKbCode());
        List<String> requestedCodes = request.getDocumentCodes().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (requestedCodes.isEmpty()) {
            throw BizException.illegalParam(AiKbBizCodeConstant.REQUIRED_DTO);
        }

        AiKbDeleteResponse response = new AiKbDeleteResponse();
        int deletedCount = 0;
        int deletedContentCount = 0;
        int deletedVersionCount = 0;
        int deletedVersionContentCount = 0;
        List<String> skippedDocumentCodes = new ArrayList<>();

        for (String documentCode : requestedCodes) {
            AiKbDocumentDTO document = resolveDocumentForDelete(requestKbCode, documentCode);
            if (document == null || document.getId() == null) {
                skippedDocumentCodes.add(documentCode);
                continue;
            }

            AiKbDocumentContentDTO content = contentService.getByDocumentId(document.getId());
            if (content != null && content.getId() != null && contentService.delete(content.getId())) {
                deletedContentCount++;
            }

            AiKbDocumentQueryRequest versionDocumentQuery = new AiKbDocumentQueryRequest();
            versionDocumentQuery.setKbCode(document.getKbCode());
            versionDocumentQuery.setDocumentCode(document.getDocumentCode());
            versionDocumentQuery.setPage(1);
            versionDocumentQuery.setSize(1);

            AiKbDocumentVersionQueryRequest versionQuery = new AiKbDocumentVersionQueryRequest();
            versionQuery.setKbCode(document.getKbCode());
            versionQuery.setDocumentCode(document.getDocumentCode());
            versionQuery.setPage(1);
            versionQuery.setSize(Integer.MAX_VALUE);
            List<AiKbDocumentVersionDTO> versions = documentVersionService.listByQuery(versionQuery);
            for (AiKbDocumentVersionDTO version : versions) {
                if (version == null || version.getId() == null) {
                    continue;
                }
                AiKbDocumentVersionContentQueryRequest versionContentQuery = new AiKbDocumentVersionContentQueryRequest();
                versionContentQuery.setDocumentVersionId(version.getId());
                versionContentQuery.setPage(1);
                versionContentQuery.setSize(Integer.MAX_VALUE);
                List<AiKbDocumentVersionContentDTO> versionContents = documentVersionContentService.queryAll(versionContentQuery);
                for (AiKbDocumentVersionContentDTO versionContent : versionContents) {
                    if (versionContent != null && versionContent.getId() != null && documentVersionContentService.delete(versionContent.getId())) {
                        deletedVersionContentCount++;
                    }
                }
                if (documentVersionService.delete(version.getId())) {
                    deletedVersionCount++;
                }
            }

            if (documentService.delete(document.getId())) {
                deletedCount++;
            } else {
                skippedDocumentCodes.add(documentCode);
            }
        }

        response.setDeletedCount(deletedCount);
        response.setDeletedContentCount(deletedContentCount);
        response.setDeletedVersionCount(deletedVersionCount);
        response.setDeletedVersionContentCount(deletedVersionContentCount);
        response.setSkippedDocumentCodes(skippedDocumentCodes);
        return response;
    }

    private PublishResult syncCurrentDocuments(String kbCode, List<AiKbDocumentDTO> documents) {
        AiKbStoreDTO store = storeService.getByKbCode(kbCode);
        if (store == null) {
            throw BizException.of(AiKbBizCodeConstant.KB_STORE_NOT_FOUND, kbCode);
        }
        Map<String, Object> storeExt = copyMap(store.getExtJson());
        requireExtText(storeExt, "workspaceId");
        requireExtText(storeExt, "kbEndpoint");

        List<KbDocument> aiDocuments = new ArrayList<>(documents.size());
        List<AiKbDocumentDTO> acceptedDocuments = new ArrayList<>(documents.size());
        List<String> skipped = new ArrayList<>();
        for (AiKbDocumentDTO document : documents) {
            AiKbDocumentContentDTO content = contentService.getByDocumentId(document.getId());
            String renderedContent = content == null ? null : content.getRenderedContent();
            if (!StringUtils.hasText(renderedContent)) {
                document.setProviderSyncStatus(AiKbProviderSyncStatus.FAILED);
                document.setLastError("current document content missing");
                documentService.update(document.getId(), document);
                skipped.add(document.getDocumentCode());
                continue;
            }
            aiDocuments.add(toKbDocument(document, content, renderedContent));
            acceptedDocuments.add(document);
        }
        if (aiDocuments.isEmpty()) {
            throw BizException.of(AiKbBizCodeConstant.CURRENT_DOCUMENT_CONTENT_MISSING, String.join(",", skipped));
        }

        KbUpsertRequest upsertRequest = new KbUpsertRequest();
        upsertRequest.setKbId(trimToNull(store.getProviderKbId()));
        upsertRequest.setDocuments(aiDocuments);
        upsertRequest.setMeta(buildPublishMeta(store, storeExt));
        markDocumentsSyncing(acceptedDocuments);
        KbUpsertResponse upsertResponse;
        try {
            upsertResponse = aiKnowledgeExecutionService.kbUpsert(upsertRequest);
        } catch (RuntimeException ex) {
            markDocumentsSyncFailed(acceptedDocuments, ex.getMessage());
            throw BizException.of(AiKbBizCodeConstant.PROVIDER_UPSERT_FAILED, ex.getMessage());
        }
        if (upsertResponse == null || upsertResponse.getAccepted() == null || upsertResponse.getAccepted() < aiDocuments.size()) {
            String message = "AI kb upsert did not accept all current documents";
            markDocumentsSyncFailed(acceptedDocuments, message);
            throw BizException.of(AiKbBizCodeConstant.PROVIDER_UPSERT_NOT_ACCEPTED);
        }

        String providerKbId = trimToNull(upsertResponse.getKbId());
        if (providerKbId != null && !Objects.equals(providerKbId, store.getProviderKbId())) {
            store.setProviderKbId(providerKbId);
        }
        store.setEnabled(Boolean.TRUE);
        storeService.update(store.getId(), store);

        Map<String, String> providerDocumentIds = upsertResponse.getDocumentIdMappings() == null
                ? Map.of()
                : upsertResponse.getDocumentIdMappings();
        for (AiKbDocumentDTO document : acceptedDocuments) {
            String providerDocumentId = trimToNull(providerDocumentIds.get(document.getDocumentCode()));
            document.setProviderDocumentId(providerDocumentId == null ? document.getProviderDocumentId() : providerDocumentId);
            document.setProviderSyncStatus(AiKbProviderSyncStatus.SUCCESS);
            document.setLastError(null);
            documentService.update(document.getId(), document);
            persistSyncSnapshot(document, contentService.getByDocumentId(document.getId()));
        }
        return new PublishResult(aiDocuments.size(), skipped);
    }

    private void persistSyncSnapshot(AiKbDocumentDTO document, AiKbDocumentContentDTO content) {
        if (document == null || content == null || content.getId() == null) {
            return;
        }
        Map<String, Object> ext = copyMap(content.getExtJson());
        ext.put(LAST_SYNC_CHECKSUM_KEY, document.getContentChecksum());
        ext.put(LAST_SYNC_VERSION_NO_KEY, document.getDocumentVersionNo());
        ext.put(LAST_SYNC_AT_KEY, LocalDateTime.now().toString());
        ext.put(LAST_SYNC_PROVIDER_DOCUMENT_ID_KEY, document.getProviderDocumentId());
        content.setExtJson(ext);
        contentService.update(content.getId(), content);
    }

    private void markDocumentsSyncing(List<AiKbDocumentDTO> documents) {
        for (AiKbDocumentDTO document : documents) {
            document.setProviderSyncStatus(AiKbProviderSyncStatus.RUNNING);
            document.setLastError(null);
            documentService.update(document.getId(), document);
        }
    }

    private void markDocumentsSyncFailed(List<AiKbDocumentDTO> documents, String message) {
        for (AiKbDocumentDTO document : documents) {
            document.setProviderSyncStatus(AiKbProviderSyncStatus.FAILED);
            document.setLastError(message);
            documentService.update(document.getId(), document);
        }
    }

    private boolean shouldSyncDocument(AiKbDocumentDTO document) {
        AiKbProviderSyncStatus syncStatus = document.getProviderSyncStatus();
        return syncStatus == null
                || syncStatus == AiKbProviderSyncStatus.PENDING
                || syncStatus == AiKbProviderSyncStatus.FAILED;
    }

    private AiKbSyncCheckResponse.Item buildSyncCheckItem(AiKbDocumentDTO document, AiKbDocumentContentDTO content) {
        AiKbSyncCheckResponse.Item item = new AiKbSyncCheckResponse.Item();
        item.setKbCode(document.getKbCode());
        item.setDocumentCode(document.getDocumentCode());
        item.setDocumentName(document.getDocumentName());
        item.setProviderDocumentId(document.getProviderDocumentId());

        if (content == null || !StringUtils.hasText(content.getRenderedContent())) {
            item.setStatus("MISSING_SNAPSHOT");
            item.setMessage("本地正文不存在");
            return item;
        }
        if (document.getProviderSyncStatus() != AiKbProviderSyncStatus.SUCCESS || !StringUtils.hasText(document.getProviderDocumentId())) {
            item.setStatus("NOT_SYNCED");
            item.setMessage("当前文档还未成功同步到远端");
            return item;
        }

        Map<String, Object> ext = copyMap(content.getExtJson());
        String lastSyncChecksum = objectText(ext.get(LAST_SYNC_CHECKSUM_KEY));
        String lastSyncProviderDocumentId = objectText(ext.get(LAST_SYNC_PROVIDER_DOCUMENT_ID_KEY));
        if (!StringUtils.hasText(lastSyncChecksum)) {
            item.setStatus("MISSING_SNAPSHOT");
            item.setMessage("缺少最近一次成功同步快照");
            return item;
        }
        if (StringUtils.hasText(lastSyncProviderDocumentId)
                && !Objects.equals(lastSyncProviderDocumentId, trimToNull(document.getProviderDocumentId()))) {
            item.setStatus("CHANGED");
            item.setMessage("远端文档标识已变更，建议重新同步");
            return item;
        }
        if (Objects.equals(lastSyncChecksum, trimToNull(document.getContentChecksum()))) {
            item.setStatus("MATCHED");
            item.setMessage("本地内容与最近一次成功同步快照一致");
            return item;
        }
        item.setStatus("CHANGED");
        item.setMessage("本地内容已变化，需重新同步到远端");
        return item;
    }

    private KbDocument toKbDocument(AiKbDocumentDTO document, AiKbDocumentContentDTO content, String renderedContent) {
        KbDocument target = new KbDocument();
        target.setDocumentId(document.getDocumentCode());
        target.setContent(renderedContent);
        String sourceSystem = resolveSourceSystem(document.getMetaJson());
        target.setSource(StringUtils.hasText(sourceSystem) ? sourceSystem : document.getKbCode());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("kbCode", document.getKbCode());
        metadata.put("documentCode", document.getDocumentCode());
        metadata.put("documentName", document.getDocumentName());
        metadata.put("documentType", enumName(document.getDocumentType()));
        metadata.put("bizType", enumName(document.getBizType()));
        metadata.put("bizKey", document.getBizKey());
        metadata.put("contentChecksum", document.getContentChecksum());
        metadata.put("contentFormat", enumName(document.getContentFormat()));
        metadata.put("contentSize", document.getContentSize());
        metadata.putAll(copyMap(document.getMetaJson()));
        metadata.putAll(copyMap(content == null ? null : content.getExtJson()));
        target.setMetadata(metadata);
        return target;
    }

    private RequestMeta buildPublishMeta(AiKbStoreDTO store, Map<String, Object> storeExt) {
        RequestMeta meta = new RequestMeta();
        meta.setScene("AI_KB_PUBLISH");
        Map<String, Object> ext = new LinkedHashMap<>(storeExt);
        ext.putIfAbsent("kbName", store.getKbName());
        ext.put("localKbCode", store.getKbCode());
        ext.put("providerKbId", store.getProviderKbId());
        meta.setExt(ext);
        return meta;
    }

    private void saveDocumentSnapshot(AiKbDocumentDTO document,
                                      AiKbDocumentContentDTO content,
                                      AiKbChangeType changeType,
                                      LocalDateTime snapshotAt) {
        AiKbDocumentVersionDTO snapshot = new AiKbDocumentVersionDTO();
        snapshot.setKbCode(document.getKbCode());
        snapshot.setDocumentCode(document.getDocumentCode());
        snapshot.setDocumentName(document.getDocumentName());
        snapshot.setDocumentType(document.getDocumentType());
        snapshot.setBizType(document.getBizType());
        snapshot.setBizKey(document.getBizKey());
        snapshot.setDocumentVersionNo(document.getDocumentVersionNo() == null ? 1 : document.getDocumentVersionNo());
        snapshot.setChangeType(changeType);
        snapshot.setContentChecksum(document.getContentChecksum());
        snapshot.setContentFormat(document.getContentFormat());
        snapshot.setContentSize(document.getContentSize());
        snapshot.setMetaJson(copyMap(document.getMetaJson()));
        snapshot.setSnapshotAt(snapshotAt);
        snapshot.setSnapshotBy("-1");
        snapshot.setRemark(document.getRemark());
        snapshot = documentVersionService.add(snapshot);

        if (content != null) {
            AiKbDocumentVersionContentDTO versionContent = new AiKbDocumentVersionContentDTO();
            versionContent.setDocumentVersionId(snapshot.getId());
            versionContent.setContentFormat(content.getContentFormat());
            versionContent.setContentSize(content.getContentSize());
            versionContent.setContentJson(copyMap(content.getContentJson()));
            versionContent.setRenderedContent(content.getRenderedContent());
            versionContent.setExtJson(copyMap(content.getExtJson()));
            documentVersionContentService.add(versionContent);
        }
    }

    private String requireExtText(Map<String, Object> ext, String key) {
        Object value = ext.get(key);
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        throw BizException.illegalParam(AiKbBizCodeConstant.REQUIRED_STORE_EXT, key);
    }

    private AiKbDocumentUpsertResponse buildUpsertResponse(String kbId,
                                                           String documentId,
                                                           boolean created,
                                                           boolean updated,
                                                           boolean exists,
                                                           boolean unchanged,
                                                           Integer currentVersionNo,
                                                           Integer previousVersionNo,
                                                           String message) {
        AiKbDocumentUpsertResponse response = new AiKbDocumentUpsertResponse();
        response.setKbId(kbId);
        response.setDocumentId(documentId);
        response.setCreated(created);
        response.setUpdated(updated);
        response.setExists(exists);
        response.setUnchanged(unchanged);
        response.setCurrentVersionNo(currentVersionNo);
        response.setPreviousVersionNo(previousVersionNo);
        response.setMessage(message);
        return response;
    }

    private AiKbDocumentDTO getDocumentById(Long documentId) {
        if (documentId == null) {
            return null;
        }
        AiKbDocumentQueryRequest query = new AiKbDocumentQueryRequest();
        query.setId(documentId);
        query.setPage(1);
        query.setSize(1);
        List<AiKbDocumentDTO> list = documentService.listByQuery(query);
        return list.isEmpty() ? null : list.get(0);
    }

    private void validateContentUpdateRequest(AiKbDocumentContentUpdateRequest request) {
        if (request == null) {
            throw BizException.illegalParam(AiKbBizCodeConstant.REQUIRED_DTO);
        }
        if (request.getDocumentId() == null) {
            throw BizException.illegalParam(AiKbBizCodeConstant.REQUIRED_DOCUMENT_ID);
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw BizException.illegalParam(AiKbBizCodeConstant.REQUIRED_CONTENT);
        }
    }

    private void validateUpsertRequest(AiKbDocumentUpsertRequest request) {
        if (request == null) {
            throw BizException.illegalParam(AiKbBizCodeConstant.REQUIRED_DTO);
        }
        if (!StringUtils.hasText(request.getKbId())) {
            throw BizException.illegalParam(AiKbBizCodeConstant.REQUIRED_KB_ID);
        }
        if (!StringUtils.hasText(request.getDocumentId())) {
            throw BizException.illegalParam(AiKbBizCodeConstant.REQUIRED_DOCUMENT_ID);
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw BizException.illegalParam(AiKbBizCodeConstant.REQUIRED_CONTENT);
        }
    }

    private void validateCreateRequest(AiKbCreateRequest request) {
        if (request == null) {
            throw BizException.illegalParam(AiKbBizCodeConstant.REQUIRED_DTO);
        }
        if (!StringUtils.hasText(request.getKbCode())) {
            throw BizException.illegalParam(AiKbBizCodeConstant.REQUIRED_KB_ID);
        }
        if (!StringUtils.hasText(request.getKbName())) {
            throw BizException.illegalParam(AiKbBizCodeConstant.REQUIRED_KB_NAME);
        }
    }

    private AiKbStoreDTO requireStore(String kbId) {
        AiKbStoreDTO store = storeService.getByKbCode(kbId);
        if (store == null) {
            log.warn("ai kb store not found, kbId={}", kbId);
            throw BizException.of(AiKbBizCodeConstant.KB_STORE_NOT_FOUND, kbId);
        }
        return store;
    }

    private String resolveUpsertSourceKey(Map<String, Object> ext, AiKbDocumentDTO existing) {
        String extSourceKey = extText(ext, "sourceKey");
        if (StringUtils.hasText(extSourceKey)) {
            return extSourceKey;
        }
        String existingSourceKey = extText(existing == null ? null : existing.getMetaJson(), "sourceKey");
        if (StringUtils.hasText(existingSourceKey)) {
            return existingSourceKey;
        }
        return null;
    }

    private AiKbDocumentType resolveDocumentType(AiKbDocumentUpsertRequest request) {
        if (request.getDocumentType() != null) {
            return request.getDocumentType();
        }
        throw BizException.illegalParam(AiKbBizCodeConstant.REQUIRED_DOCUMENT_TYPE);
    }

    private AiKbBizType resolveBizType(AiKbDocumentType documentType, AiKbBizType requestBizType) {
        // bizType 允许不传；不传时按 documentType 的预定义归属自动推导。
        AiKbBizType inferred = documentType.getBizType();
        if (requestBizType == null) {
            return inferred;
        }
        if (requestBizType != inferred) {
            log.warn("ai kb biz type invalid, documentType={}, inferredBizType={}, actualBizType={}",
                    documentType, inferred, requestBizType);
            throw BizException.illegalParam(AiKbBizCodeConstant.INVALID_DOCUMENT_SOURCE_TYPE,
                    documentType, inferred, requestBizType);
        }
        return requestBizType;
    }

    private Map<String, Object> normalizeExt(Map<String, Object> ext) {
        return ext == null ? new LinkedHashMap<>() : new LinkedHashMap<>(ext);
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private String extText(Map<String, Object> ext, String key) {
        if (ext == null || !StringUtils.hasText(key)) {
            return null;
        }
        Object value = ext.get(key);
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        return null;
    }

    private String objectText(Object value) {
        if (value == null) {
            return null;
        }
        String text = Objects.toString(value, null);
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private AiKbDocumentDTO resolveDocumentForDelete(String kbCode, String documentCode) {
        if (StringUtils.hasText(kbCode)) {
            return documentService.getByKbCodeAndDocumentCode(kbCode, documentCode);
        }
        AiKbDocumentQueryRequest query = new AiKbDocumentQueryRequest();
        query.setDocumentCode(documentCode);
        query.setPage(1);
        query.setSize(2);
        List<AiKbDocumentDTO> documents = documentService.listByQuery(query);
        return documents.isEmpty() ? null : documents.get(0);
    }

    private AiKbDocumentListItemDTO toDocumentListItem(AiKbDocumentDTO source) {
        AiKbDocumentListItemDTO target = new AiKbDocumentListItemDTO();
        target.setId(source.getId());
        target.setKbCode(source.getKbCode());
        target.setDocumentCode(source.getDocumentCode());
        target.setDocumentName(source.getDocumentName());
        target.setDocumentType(source.getDocumentType());
        target.setBizType(source.getBizType());
        target.setBizKey(source.getBizKey());
        target.setSourceSystem(resolveSourceSystem(source.getMetaJson()));
        target.setStatus(source.getStatus());
        target.setProviderDocumentId(source.getProviderDocumentId());
        target.setProviderSyncStatus(source.getProviderSyncStatus());
        target.setCurrentVersionNo(source.getDocumentVersionNo());
        target.setContentFormat(source.getContentFormat());
        target.setContentSize(source.getContentSize());
        target.setLastGeneratedAt(source.getLastGeneratedAt());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }

    private AiKbDocumentDetailDTO toDocumentDetail(AiKbDocumentDTO document, AiKbDocumentContentDTO content) {
        AiKbDocumentDetailDTO target = new AiKbDocumentDetailDTO();
        AiKbDocumentListItemDTO summary = toDocumentListItem(document);
        target.setId(summary.getId());
        target.setKbCode(summary.getKbCode());
        target.setDocumentCode(summary.getDocumentCode());
        target.setDocumentName(summary.getDocumentName());
        target.setDocumentType(summary.getDocumentType());
        target.setBizType(summary.getBizType());
        target.setBizKey(summary.getBizKey());
        target.setSourceSystem(summary.getSourceSystem());
        target.setStatus(summary.getStatus());
        target.setCurrentVersionNo(summary.getCurrentVersionNo());
        target.setContentFormat(summary.getContentFormat());
        target.setContentSize(summary.getContentSize());
        target.setLastGeneratedAt(summary.getLastGeneratedAt());
        target.setUpdateTime(summary.getUpdateTime());
        target.setContentChecksum(document.getContentChecksum());
        target.setMetaJson(copyMap(document.getMetaJson()));
        target.setLastError(document.getLastError());
        target.setRemark(document.getRemark());
        target.setContentJson(copyMap(content == null ? null : content.getContentJson()));
        target.setRenderedContent(content == null ? null : content.getRenderedContent());
        target.setExtJson(copyMap(content == null ? null : content.getExtJson()));
        return target;
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private AiKbInfoDTO toKbInfo(AiKbStoreDTO store) {
        AiKbInfoDTO dto = new AiKbInfoDTO();
        dto.setKbId(store.getKbCode());
        dto.setKbName(store.getKbName());
        dto.setProviderKbId(store.getProviderKbId());
        dto.setEnabled(store.getEnabled());
        dto.setTags(store.getTags());
        dto.setUrl(store.getUrl());
        dto.setExt(store.getExtJson() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(store.getExtJson()));
        return dto;
    }

    private String resolveSourceSystem(Map<String, Object> ext) {
        // sourceSystem 收敛到 metaJson 中，作为可选来源标识透出给页面展示和排查。
        Object value = ext.get("sourceSystem");
        return value instanceof String sourceSystem && StringUtils.hasText(sourceSystem)
                ? sourceSystem.trim()
                : null;
    }

    private AiKbBizType resolveBizType(Integer bizTypeCode) {
        return AiKbBizType.fromCode(bizTypeCode);
    }

    private AiKbDocumentStatus resolveDocumentStatus(String tab) {
        if (!StringUtils.hasText(tab)) {
            return AiKbDocumentStatus.ACTIVE;
        }
        if ("history".equalsIgnoreCase(tab.trim())) {
            return AiKbDocumentStatus.DISABLED;
        }
        return AiKbDocumentStatus.ACTIVE;
    }

    private int safePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int safeSize(Integer size) {
        return size == null || size < 1 ? 10 : Math.min(size, 200);
    }

    private String checksum(String content) {
        try {
            // 使用正文摘要判断文档是否真的发生变化，避免重复刷版本。
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw BizException.of(AiKbBizCodeConstant.CHECKSUM_CALCULATE_FAILED);
        }
    }

    private record PublishResult(Integer acceptedCount, List<String> skippedDocumentCodes) {
    }
}
