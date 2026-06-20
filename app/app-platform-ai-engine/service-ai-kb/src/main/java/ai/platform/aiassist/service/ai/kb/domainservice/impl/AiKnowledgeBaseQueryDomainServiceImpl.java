package ai.platform.aiassist.service.ai.kb.domainservice.impl;

import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentDetailDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentListItemDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentListRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbInfoDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbListRequest;
import ai.platform.aiassist.service.ai.api.enums.AiKbBizType;
import ai.platform.aiassist.service.ai.api.enums.AiKbSourceType;
import ai.platform.aiassist.service.ai.api.enums.AiKbStoreStatus;
import ai.platform.aiassist.service.ai.kb.domainservice.AiKnowledgeBaseQueryDomainService;
import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbDocumentContentDTO;
import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbDocumentDTO;
import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbStoreDTO;
import ai.platform.aiassist.service.ai.kb.entity.req.AiKbDocumentQueryRequest;
import ai.platform.aiassist.service.ai.kb.service.AiKbDocumentContentService;
import ai.platform.aiassist.service.ai.kb.service.AiKbDocumentService;
import ai.platform.aiassist.service.ai.kb.service.AiKbStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AiKnowledgeBaseQueryDomainServiceImpl implements AiKnowledgeBaseQueryDomainService {

    private final AiKbStoreService storeService;
    private final AiKbDocumentService documentService;
    private final AiKbDocumentContentService contentService;

    public AiKnowledgeBaseQueryDomainServiceImpl(AiKbStoreService storeService,
                                                 AiKbDocumentService documentService,
                                                 AiKbDocumentContentService contentService) {
        this.storeService = storeService;
        this.documentService = documentService;
        this.contentService = contentService;
    }

    @Override
    public List<AiKbInfoDTO> kbList(AiKbListRequest request) {
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

    private AiKbSourceType bizTypeToSourceType(AiKbBizType bizType) {
        return bizType == null ? null : AiKbSourceType.valueOf(bizType.name());
    }
}
