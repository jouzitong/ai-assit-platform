package ai.platform.aiassit.service.ai.provider.service;

import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.dto.EmbedResponse;
import ai.platform.aiassit.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassit.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassit.service.ai.api.dto.KbUpsertResponse;
import ai.platform.aiassit.service.ai.api.dto.RerankResponse;
import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import ai.platform.aiassit.service.ai.provider.client.RagflowKnowledgeBaseClient;
import ai.platform.aiassit.service.ai.spi.KnowledgeService;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderEmbedRequest;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderKbDeleteRequest;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderKbSearchRequest;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderKbUpsertRequest;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderRerankRequest;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** RAGFlow 知识库 SPI 实现。 */
@Component
//@ConditionalOnProperty(prefix = "ai.provider.ragflow", name = "enabled", havingValue = "true")
public class RagflowProvider implements KnowledgeService {

    private final RagflowKnowledgeBaseClient knowledgeBaseClient;

    public RagflowProvider(RagflowKnowledgeBaseClient knowledgeBaseClient) {
        this.knowledgeBaseClient = knowledgeBaseClient;
    }

    @Override
    public AiKnowledgeClientType knowledgeClientType() {
        return AiKnowledgeClientType.RAGFLOW;
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
        if (request == null || !StringUtils.hasText(request.getKbId())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_KB_ID);
        }
        try {
            RagflowKnowledgeBaseClient.UpsertResult result = knowledgeBaseClient.upsert(
                    request.getKbId(), request.getDocuments(), request.getMeta());
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
        try {
            KbDeleteResponse response = new KbDeleteResponse();
            response.setKbId(request.getKbId());
            response.setDeleted(knowledgeBaseClient.deleteDocuments(request.getKbId(), request.getDocumentIds(), request.getMeta()));
            return response;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_DELETE_FAILED, ex.getMessage());
        }
    }

    @Override
    public KbSearchResponse kbSearch(ProviderKbSearchRequest request) {
        if (request == null || !StringUtils.hasText(request.getKbId()) || !StringUtils.hasText(request.getQuery())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_MESSAGE);
        }
        try {
            RagflowKnowledgeBaseClient.SearchResult result = knowledgeBaseClient.searchDetailed(request);
            KbSearchResponse response = new KbSearchResponse();
            response.setKbId(request.getKbId());
            response.setTotal(result.total());
            response.setItems(result.items());
            return response;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_SEARCH_FAILED, ex.getMessage());
        }
    }

    private BizException unsupported(String operation) {
        return BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED,
                "RAGFlow does not provide " + operation + " through KnowledgeService");
    }
}
