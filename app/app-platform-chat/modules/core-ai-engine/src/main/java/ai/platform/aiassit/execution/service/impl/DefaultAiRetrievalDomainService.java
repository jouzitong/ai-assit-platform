package ai.platform.aiassit.execution.service.impl;

import ai.platform.aiassit.execution.service.AiKnowledgeExecutionService;
import ai.platform.aiassit.execution.service.AiRetrievalDomainService;
import ai.platform.aiassit.execution.validator.AiRequestValidator;
import ai.platform.aiassit.service.ai.api.AiRetrievalExecutionApi;
import ai.platform.aiassit.service.ai.api.dto.HybridSearchHit;
import ai.platform.aiassit.service.ai.api.dto.HybridSearchRequest;
import ai.platform.aiassit.service.ai.api.dto.HybridSearchResponse;
import ai.platform.aiassit.service.ai.api.dto.KbSearchItem;
import ai.platform.aiassit.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassit.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassit.service.ai.api.dto.RerankItem;
import ai.platform.aiassit.service.ai.api.dto.RerankRequest;
import ai.platform.aiassit.service.ai.api.dto.RerankResponse;
import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic retrieval facade. Open-ended analysis belongs to the Agent runtime. */
@RestController
public class DefaultAiRetrievalDomainService implements AiRetrievalDomainService, AiRetrievalExecutionApi {

    private final AiKnowledgeExecutionService aiKnowledgeExecutionService;
    private final AiRequestValidator aiRequestValidator;

    public DefaultAiRetrievalDomainService(AiKnowledgeExecutionService aiKnowledgeExecutionService,
                                           AiRequestValidator aiRequestValidator) {
        this.aiKnowledgeExecutionService = aiKnowledgeExecutionService;
        this.aiRequestValidator = aiRequestValidator;
    }

    @Override
    public HybridSearchResponse hybridSearch(@RequestBody HybridSearchRequest request) {
        aiRequestValidator.validateHybridSearch(request);
        HybridSearchResponse response = new HybridSearchResponse();
        response.setKbId(request.getKbId());
        response.setQuery(request.getQuery());
        response.setRetrievalMode(resolveRetrievalMode(request));

        KbSearchRequest kbSearchRequest = new KbSearchRequest();
        kbSearchRequest.setKbId(request.getKbId());
        kbSearchRequest.setQuery(request.getQuery());
        kbSearchRequest.setTopK(resolveTopK(request));
        kbSearchRequest.setMeta(copyMeta(request.getMeta()));
        KbSearchResponse kbSearchResponse = aiKnowledgeExecutionService.kbSearch(kbSearchRequest);
        response.setHits(toHybridHits(kbSearchResponse));

        if (Boolean.TRUE.equals(request.getRerankEnabled()) && !CollectionUtils.isEmpty(response.getHits())) {
            try {
                rerankHits(request, response);
            } catch (Exception ex) {
                response.setDegraded(Boolean.TRUE);
                response.setDegradedReason("rerank degraded: " + ex.getMessage());
            }
        }
        return response;
    }

    private void rerankHits(HybridSearchRequest request, HybridSearchResponse response) {
        RerankRequest rerankRequest = new RerankRequest();
        rerankRequest.setClientType(request.getClientType());
        rerankRequest.setQuery(request.getQuery());
        rerankRequest.setTopN(resolveTopK(request));
        rerankRequest.setMeta(copyMeta(request.getMeta()));
        rerankRequest.setCandidates(response.getHits().stream().map(HybridSearchHit::getContent).toList());
        RerankResponse rerankResponse = aiKnowledgeExecutionService.rerank(rerankRequest);
        if (rerankResponse == null || CollectionUtils.isEmpty(rerankResponse.getItems())) {
            return;
        }
        List<HybridSearchHit> rerankedHits = new ArrayList<>();
        for (RerankItem item : rerankResponse.getItems()) {
            if (item == null || item.getIndex() == null || item.getIndex() < 0
                    || item.getIndex() >= response.getHits().size()) {
                continue;
            }
            HybridSearchHit hit = response.getHits().get(item.getIndex());
            hit.setRerankScore(item.getScore());
            hit.setFinalScore(item.getScore());
            rerankedHits.add(hit);
        }
        if (!rerankedHits.isEmpty()) {
            rerankedHits.sort(Comparator.comparing(
                    HybridSearchHit::getFinalScore,
                    Comparator.nullsLast(Double::compareTo)).reversed());
            response.setHits(rerankedHits);
            response.setReranked(Boolean.TRUE);
        }
    }

    private List<HybridSearchHit> toHybridHits(KbSearchResponse kbSearchResponse) {
        List<HybridSearchHit> hits = new ArrayList<>();
        if (kbSearchResponse == null || CollectionUtils.isEmpty(kbSearchResponse.getItems())) {
            return hits;
        }
        for (KbSearchItem item : kbSearchResponse.getItems()) {
            if (item == null) {
                continue;
            }
            HybridSearchHit hit = new HybridSearchHit();
            hit.setDocumentId(item.getDocumentId());
            hit.setContent(item.getContent());
            hit.setMetadata(item.getMetadata());
            hit.setSourceType("KB");
            hit.setScore(item.getScore());
            hit.setFinalScore(item.getScore());
            hits.add(hit);
        }
        return hits;
    }

    private String resolveRetrievalMode(HybridSearchRequest request) {
        boolean keywordEnabled = !Boolean.FALSE.equals(request.getKeywordEnabled());
        boolean vectorEnabled = !Boolean.FALSE.equals(request.getVectorEnabled());
        if (keywordEnabled && vectorEnabled) {
            return "HYBRID";
        }
        if (keywordEnabled) {
            return "KEYWORD";
        }
        if (vectorEnabled) {
            return "VECTOR";
        }
        return "DEFAULT";
    }

    private Integer resolveTopK(HybridSearchRequest request) {
        return request.getTopK() != null && request.getTopK() > 0 ? request.getTopK() : 5;
    }

    private RequestMeta copyMeta(RequestMeta source) {
        RequestMeta target = new RequestMeta();
        if (source == null) {
            return target;
        }
        target.setTraceId(source.getTraceId());
        target.setScene(source.getScene());
        target.setTenantId(source.getTenantId());
        if (source.getExt() != null && !source.getExt().isEmpty()) {
            target.setExt(new java.util.HashMap<>(source.getExt()));
        }
        return target;
    }
}
