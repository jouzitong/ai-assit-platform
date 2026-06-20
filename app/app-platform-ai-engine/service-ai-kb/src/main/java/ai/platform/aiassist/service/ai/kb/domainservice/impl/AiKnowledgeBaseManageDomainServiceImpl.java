package ai.platform.aiassist.service.ai.kb.domainservice.impl;

import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentDetailDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentListItemDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentListRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassist.service.ai.api.dto.AiKbInfoDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbListRequest;
import ai.platform.aiassist.service.ai.api.enums.AiKbBizType;
import ai.platform.aiassist.service.ai.api.enums.AiKbContentFormat;
import ai.platform.aiassist.service.ai.api.enums.AiKbDocumentStatus;
import ai.platform.aiassist.service.ai.api.enums.AiKbReviewStatus;
import ai.platform.aiassist.service.ai.api.enums.AiKbSourceType;
import ai.platform.aiassist.service.ai.api.enums.AiKbStoreStatus;
import ai.platform.aiassist.service.ai.api.enums.AiKbVersionStatus;
import ai.platform.aiassist.service.ai.kb.domainservice.AiKnowledgeBaseManageDomainService;
import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbDocumentContentDTO;
import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbDocumentDTO;
import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbStoreDTO;
import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbVersionDTO;
import ai.platform.aiassist.service.ai.kb.entity.req.AiKbDocumentQueryRequest;
import ai.platform.aiassist.service.ai.kb.service.AiKbDocumentContentService;
import ai.platform.aiassist.service.ai.kb.service.AiKbDocumentService;
import ai.platform.aiassist.service.ai.kb.service.AiKbStoreService;
import ai.platform.aiassist.service.ai.kb.service.AiKbVersionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class AiKnowledgeBaseManageDomainServiceImpl implements AiKnowledgeBaseManageDomainService {

    private final AiKbStoreService storeService;
    private final AiKbDocumentService documentService;
    private final AiKbDocumentContentService contentService;
    private final AiKbVersionService versionService;

    public AiKnowledgeBaseManageDomainServiceImpl(AiKbStoreService storeService,
                                                  AiKbDocumentService documentService,
                                                  AiKbDocumentContentService contentService,
                                                  AiKbVersionService versionService) {
        this.storeService = storeService;
        this.documentService = documentService;
        this.contentService = contentService;
        this.versionService = versionService;
    }

    /**
     * 新增或更新本地知识库草稿文档。
     *
     * <p>实现逻辑：</p>
     * <p>1. 校验入参，确保 kbId、documentId、documentType、sourceKey、content 等关键字段齐全。</p>
     * <p>2. 根据 documentType 推导或校验 sourceType，并计算正文 checksum 与内容大小。</p>
     * <p>3. 检查本地知识库主记录是否存在；若不存在，则自动创建一条初始化状态的知识库记录。</p>
     * <p>4. 按 kbId + documentId 查询草稿文档：</p>
     * <p>   - 不存在则新增草稿文档，初始版本号为 1；</p>
     * <p>   - 已存在且 canUpdate 未显式传 true 时拒绝覆盖；</p>
     * <p>   - 已存在且允许更新时才比较 checksum 和元数据，发生变化时递增草稿版本并重置为草稿审核态。</p>
     * <p>5. 成功写入前检查当前知识库是否有草稿版本；没有则基于最高版本号创建一个新的草稿版本。</p>
     * <p>6. 将正文内容写入独立内容表，避免主文档表承载大字段；正文不存在则新增，存在则按需更新。</p>
     * <p>7. 返回本次 upsert 结果，包括是否新增、是否更新以及最新草稿版本号。</p>
     *
     * <p>注意：该方法只维护 ai-engine 本地草稿池，不会直接同步到 AI 侧知识库。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKbDocumentUpsertResponse upsertDocument(AiKbDocumentUpsertRequest request) {
        // 入口先做参数校验，避免脏请求把知识库草稿池写乱。
        validateUpsertRequest(request);
        String kbId = request.getKbId().trim();
        String documentId = request.getDocumentId().trim();
        String documentName = StringUtils.hasText(request.getDocumentName()) ? request.getDocumentName().trim() : documentId;
        String sourceKey = request.getSourceKey().trim();
        AiKbSourceType sourceType = resolveSourceType(request);
        String checksum = checksum(request.getContent());
        long contentSize = request.getContent().getBytes(StandardCharsets.UTF_8).length;
        boolean canUpdate = Boolean.TRUE.equals(request.getCanUpdate());
        log.info("ai kb upsert document start, kbId={}, documentId={}, documentType={}, sourceType={}, sourceKey={}, canUpdate={}",
                kbId, documentId, request.getDocumentType(), sourceType, sourceKey, canUpdate);

        AiKbDocumentDTO existing = documentService.getByKbCodeAndDocumentCode(kbId, documentId);
        if (existing != null && !canUpdate) {
            log.warn("ai kb document exists and update is denied, kbId={}, documentId={}, existingId={}",
                    kbId, documentId, existing.getId());
            throw new IllegalArgumentException("document already exists and canUpdate is false");
        }
        AiKbStoreDTO store = ensureStore(kbId, sourceType, sourceKey);
        AiKbVersionDTO draftVersion = ensureDraftVersion(kbId);

        boolean created = existing == null;
        boolean updated = false;
        AiKbDocumentDTO document = created ? new AiKbDocumentDTO() : existing;
        if (created) {
            document.setKbCode(kbId);
            document.setKbVersionId(draftVersion.getId());
            document.setDocumentCode(documentId);
            document.setDraftVersionNo(1);
            document.setStatus(AiKbDocumentStatus.ACTIVE);
            document.setReviewStatus(AiKbReviewStatus.DRAFT);
            updated = true;
        }

        Map<String, Object> ext = normalizeExt(request.getExt());
        String sourceSystem = resolveSourceSystem(ext);
        boolean contentChanged = created || !Objects.equals(existing.getContentChecksum(), checksum);
        boolean metadataChanged = created
                || !Objects.equals(document.getDocumentName(), documentName)
                || !Objects.equals(document.getDocumentType(), request.getDocumentType())
                || !Objects.equals(document.getBizType(), sourceTypeToBizType(sourceType))
                || !Objects.equals(document.getBizKey(), sourceKey)
                || !Objects.equals(document.getSourceSystem(), sourceSystem)
                || !Objects.equals(document.getMetaJson(), ext)
                || !Objects.equals(document.getKbVersionId(), draftVersion.getId());
        log.info("ai kb upsert document diff, kbId={}, documentId={}, draftVersionId={}, created={}, contentChanged={}, metadataChanged={}",
                kbId, documentId, draftVersion.getId(), created, contentChanged, metadataChanged);

        // 只要内容或元数据有变化，就重置为草稿态并递增草稿版本。
        if (!created && (contentChanged || metadataChanged)) {
            document.setDraftVersionNo((document.getDraftVersionNo() == null ? 0 : document.getDraftVersionNo()) + 1);
            document.setReviewStatus(AiKbReviewStatus.DRAFT);
            updated = true;
        }

        document.setKbVersionId(draftVersion.getId());
        document.setDocumentName(documentName);
        document.setDocumentType(request.getDocumentType());
        document.setBizType(sourceTypeToBizType(sourceType));
        document.setBizKey(sourceKey);
        document.setSourceSystem(sourceSystem);
        document.setContentChecksum(checksum);
        document.setContentFormat(AiKbContentFormat.MARKDOWN);
        document.setContentSize(contentSize);
        document.setMetaJson(ext);
        document.setLastGeneratedAt(LocalDateTime.now());
        document.setLastError(null);
        if (store.getStatus() == AiKbStoreStatus.DISABLED) {
            document.setStatus(AiKbDocumentStatus.DISABLED);
        }

        if (created) {
            document = documentService.add(document);
            log.info("ai kb document created, kbId={}, documentId={}, draftVersionNo={}",
                    kbId, documentId, document.getDraftVersionNo());
        } else if (updated) {
            document = documentService.update(document.getId(), document);
            log.info("ai kb document updated, kbId={}, documentId={}, draftVersionNo={}",
                    kbId, documentId, document.getDraftVersionNo());
        } else {
            log.info("ai kb document unchanged, kbId={}, documentId={}, draftVersionNo={}",
                    kbId, documentId, document.getDraftVersionNo());
        }

        // 正文单独落内容表，避免主文档表被大字段拖慢。
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

        AiKbDocumentUpsertResponse response = new AiKbDocumentUpsertResponse();
        response.setKbId(kbId);
        response.setDocumentId(documentId);
        response.setCreated(created);
        response.setUpdated(updated);
        response.setDraftVersionNo(document.getDraftVersionNo());
        log.info("ai kb upsert document finish, kbId={}, documentId={}, created={}, updated={}, draftVersionNo={}",
                kbId, documentId, created, updated, document.getDraftVersionNo());
        return response;
    }

    @Override
    public List<AiKbInfoDTO> kbList(AiKbListRequest request) {
        // 这里仅返回本地知识库主表信息，不下钻文档和版本明细。
        List<AiKbStoreDTO> stores = storeService.list(request);
        List<AiKbInfoDTO> result = new ArrayList<>(stores.size());
        for (AiKbStoreDTO store : stores) {
            AiKbInfoDTO dto = new AiKbInfoDTO();
            dto.setKbId(store.getKbCode());
            dto.setKbName(store.getKbName());
            dto.setSourceType(bizTypeToSourceType(store.getBizType()));
            dto.setProviderKbId(store.getProviderKbId());
            dto.setStatus(store.getStatus());
            dto.setEnabled(store.getStatus() != AiKbStoreStatus.DISABLED);
            dto.setExt(store.getExtJson() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(store.getExtJson()));
            result.add(dto);
        }
        log.info("ai kb list finish, sourceType={}, sourceKey={}, enabled={}, resultSize={}",
                request == null ? null : request.getSourceType(),
                request == null ? null : request.getSourceKey(),
                request == null ? null : request.getEnabled(),
                result.size());
        return result;
    }

    @Override
    public List<AiKbDocumentListItemDTO> listDocuments(AiKbDocumentListRequest request) {
        AiKbDocumentQueryRequest query = new AiKbDocumentQueryRequest();
        if (request != null) {
            query.setKbCode(trimToNull(request.getKbCode()));
            query.setDocumentCode(trimToNull(request.getDocumentCode()));
        }
        query.setPage(1);
        query.setSize(Integer.MAX_VALUE);
        return documentService.queryAll(query).stream()
                .map(this::toDocumentListItem)
                .toList();
    }

    @Override
    public AiKbDocumentDetailDTO getDocumentDetail(String kbCode, String documentCode) {
        if (!StringUtils.hasText(kbCode) || !StringUtils.hasText(documentCode)) {
            throw new IllegalArgumentException("kbCode and documentCode must not be blank");
        }
        AiKbDocumentDTO document = documentService.getByKbCodeAndDocumentCode(kbCode.trim(), documentCode.trim());
        if (document == null) {
            throw new IllegalArgumentException("document not found");
        }
        AiKbDocumentContentDTO content = contentService.getByDocumentId(document.getId());
        return toDocumentDetail(document, content);
    }

    private void validateUpsertRequest(AiKbDocumentUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (!StringUtils.hasText(request.getKbId())) {
            throw new IllegalArgumentException("kbId must not be blank");
        }
        if (!StringUtils.hasText(request.getDocumentId())) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        if (request.getDocumentType() == null) {
            throw new IllegalArgumentException("documentType must not be null");
        }
        if (!StringUtils.hasText(request.getSourceKey())) {
            throw new IllegalArgumentException("sourceKey must not be blank");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new IllegalArgumentException("content must not be blank");
        }
    }

    private AiKbStoreDTO ensureStore(String kbId, AiKbSourceType sourceType, String sourceKey) {
        // 上游只能向已存在的本地知识库写入草稿文档，知识库创建由本系统管理流程负责。
        AiKbStoreDTO store = storeService.getByKbCode(kbId);
        if (store == null) {
            log.warn("ai kb store not found, kbId={}, sourceType={}, sourceKey={}", kbId, sourceType, sourceKey);
            throw new IllegalArgumentException("kb store not found");
        }
        if (store.getBizType() != null && store.getBizType() != sourceTypeToBizType(sourceType)) {
            log.warn("ai kb store source type mismatch, kbId={}, expectSourceType={}, actualBizType={}",
                    kbId, sourceType, store.getBizType());
            throw new IllegalArgumentException("kbId sourceType mismatch with existing kb store");
        }
        return store;
    }

    private AiKbVersionDTO ensureDraftVersion(String kbId) {
        AiKbVersionDTO draftVersion = versionService.getDraftVersion(kbId);
        if (draftVersion != null) {
            return draftVersion;
        }
        Integer maxVersionNo = versionService.getMaxVersionNo(kbId);
        int nextVersionNo = maxVersionNo == null ? 1 : maxVersionNo + 1;
        AiKbVersionDTO created = new AiKbVersionDTO();
        created.setKbCode(kbId);
        created.setVersionNo(nextVersionNo);
        created.setVersionName(defaultVersionName(nextVersionNo));
        created.setStatus(AiKbVersionStatus.DRAFT);
        log.info("ai kb draft version auto create, kbId={}, versionNo={}, versionName={}",
                kbId, created.getVersionNo(), created.getVersionName());
        return versionService.add(created);
    }

    private String defaultVersionName(Integer versionNo) {
        int major = versionNo == null || versionNo < 1 ? 1 : versionNo;
        return "V" + major + ".0.0-Default";
    }

    private AiKbSourceType resolveSourceType(AiKbDocumentUpsertRequest request) {
        // sourceType 允许不传；不传时按 documentType 的预定义归属自动推导。
        AiKbSourceType inferred = request.getDocumentType().getSourceType();
        if (request.getSourceType() == null) {
            return inferred;
        }
        if (request.getSourceType() != inferred) {
            log.warn("ai kb source type invalid, documentType={}, inferredSourceType={}, actualSourceType={}",
                    request.getDocumentType(), inferred, request.getSourceType());
            throw new IllegalArgumentException("sourceType does not match documentType");
        }
        return request.getSourceType();
    }

    private Map<String, Object> normalizeExt(Map<String, Object> ext) {
        return ext == null ? new LinkedHashMap<>() : new LinkedHashMap<>(ext);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private AiKbDocumentListItemDTO toDocumentListItem(AiKbDocumentDTO source) {
        AiKbDocumentListItemDTO target = new AiKbDocumentListItemDTO();
        target.setId(source.getId());
        target.setKbCode(source.getKbCode());
        target.setDocumentCode(source.getDocumentCode());
        target.setDocumentName(source.getDocumentName());
        target.setDocumentType(enumName(source.getDocumentType()));
        target.setBizType(enumName(source.getBizType()));
        target.setBizKey(source.getBizKey());
        target.setSourceSystem(source.getSourceSystem());
        target.setStatus(enumName(source.getStatus()));
        target.setDraftVersionNo(source.getDraftVersionNo());
        target.setContentFormat(enumName(source.getContentFormat()));
        target.setContentSize(source.getContentSize());
        target.setReviewStatus(enumName(source.getReviewStatus()));
        target.setLastGeneratedAt(source.getLastGeneratedAt());
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
        target.setDraftVersionNo(summary.getDraftVersionNo());
        target.setContentFormat(summary.getContentFormat());
        target.setContentSize(summary.getContentSize());
        target.setReviewStatus(summary.getReviewStatus());
        target.setLastGeneratedAt(summary.getLastGeneratedAt());
        target.setContentChecksum(document.getContentChecksum());
        target.setMetaJson(copyMap(document.getMetaJson()));
        target.setLastError(document.getLastError());
        target.setRemark(document.getRemark());
        target.setContentJson(copyMap(content == null ? null : content.getContentJson()));
        target.setRenderedContent(content == null ? null : content.getRenderedContent());
        target.setExtJson(copyMap(content == null ? null : content.getExtJson()));
        return target;
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String resolveSourceSystem(Map<String, Object> ext) {
        // 上游系统名作为轻量来源标识放在 ext 里，便于后面排查是谁推的文档。
        Object value = ext.get("sourceSystem");
        return value instanceof String sourceSystem && StringUtils.hasText(sourceSystem)
                ? sourceSystem.trim()
                : null;
    }

    private AiKbBizType sourceTypeToBizType(AiKbSourceType sourceType) {
        return sourceType == null ? null : AiKbBizType.valueOf(sourceType.name());
    }

    private AiKbSourceType bizTypeToSourceType(AiKbBizType bizType) {
        return bizType == null ? null : AiKbSourceType.valueOf(bizType.name());
    }

    private String checksum(String content) {
        try {
            // 使用正文摘要判断文档是否真的发生变化，避免重复刷版本。
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
