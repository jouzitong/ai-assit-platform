package ai.platform.aiassit.service.ai.provider.service;

import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.dto.EmbedResponse;
import ai.platform.aiassit.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassit.service.ai.api.dto.KbDocument;
import ai.platform.aiassit.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassit.service.ai.api.dto.KbUpsertResponse;
import ai.platform.aiassit.service.ai.api.dto.RerankResponse;
import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import ai.platform.aiassit.service.ai.provider.client.BailianKnowledgeBaseClient;
import ai.platform.aiassit.service.ai.spi.KnowledgeService;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderEmbedRequest;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderKbDeleteRequest;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderKbSearchRequest;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderKbUpsertRequest;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderRerankRequest;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/** 百炼知识库 SPI 实现；不再承载对话或通用 OpenAI 客户端能力。 */
@Component
public class BailianKnowledgeProvider implements KnowledgeService {

    private final BailianKnowledgeBaseClient knowledgeBaseClient;

    public BailianKnowledgeProvider(BailianKnowledgeBaseClient knowledgeBaseClient) {
        this.knowledgeBaseClient = knowledgeBaseClient;
    }

    @Override
    public AiKnowledgeClientType knowledgeClientType() {
        return AiKnowledgeClientType.BAILIAN;
    }

    @Override
    public EmbedResponse embed(ProviderEmbedRequest request) {
        throw unsupported("embed");
    }

    @Override
    public RerankResponse rerank(ProviderRerankRequest request) {
        throw unsupported("rerank");
    }

    @Override
    public KbUpsertResponse kbUpsert(ProviderKbUpsertRequest request) {
        if (request == null || request.getDocuments() == null || request.getDocuments().isEmpty()) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_CONTENT);
        }
        try {
            String workspaceId = knowledgeBaseClient.resolveWorkspaceId(request.getMeta());
            BailianKnowledgeBaseClient.UpsertResult result = knowledgeBaseClient.upsert(
                    workspaceId, request.getKbId(), request.getDocuments(), request.getMeta());
            KbUpsertResponse response = new KbUpsertResponse();
            response.setKbId(result.kbId());
            response.setAccepted(result.accepted());
            response.setFailed(result.failedDocumentIds().size());
            response.setFailedDocumentIds(result.failedDocumentIds());
            response.setDocumentIdMappings(result.documentIdMappings());
            return response;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_UPSERT_FAILED, ex.getMessage());
        }
    }

    @Override
    public KbDeleteResponse kbDelete(ProviderKbDeleteRequest request) {
        if (request == null || !StringUtils.hasText(request.getKbId())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_KB_ID);
        }
        List<String> documentIds = request.getDocumentIds() == null
                ? Collections.emptyList() : request.getDocumentIds();
        if (documentIds.isEmpty()) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_DOCUMENT_ID);
        }
        try {
            String workspaceId = knowledgeBaseClient.resolveWorkspaceId(request.getMeta());
            int deleted = knowledgeBaseClient.delete(
                    workspaceId, request.getKbId(), documentIds, request.getMeta());
            KbDeleteResponse response = new KbDeleteResponse();
            response.setKbId(request.getKbId());
            response.setDeleted(deleted);
            return response;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_DELETE_FAILED, ex.getMessage());
        }
    }

    @Override
    public KbSearchResponse kbSearch(ProviderKbSearchRequest request) {
        if (request == null || !StringUtils.hasText(request.getKbId())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_KB_ID);
        }
        if (!StringUtils.hasText(request.getQuery())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_MESSAGE);
        }
        try {
            String workspaceId = knowledgeBaseClient.resolveWorkspaceId(request.getMeta());
            KbSearchResponse response = new KbSearchResponse();
            response.setKbId(request.getKbId());
            response.setItems(knowledgeBaseClient.search(
                    workspaceId, request.getKbId(), request.getQuery(), request.getTopK(), request.getMeta()));
            return response;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_SEARCH_FAILED, ex.getMessage());
        }
    }

    private BizException unsupported(String operation) {
        return BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED,
                "Bailian knowledge provider does not support " + operation);
    }
}
