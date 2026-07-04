package ai.platform.aiassit.service.ai.core.controller;

import ai.platform.aiassit.service.ai.api.AiRetrievalExecutionApi;
import ai.platform.aiassit.service.ai.api.dto.HybridSearchRequest;
import ai.platform.aiassit.service.ai.api.dto.HybridSearchResponse;
import ai.platform.aiassit.service.ai.api.dto.IntentAnalyzeRequest;
import ai.platform.aiassit.service.ai.api.dto.IntentAnalyzeResponse;
import ai.platform.aiassit.service.ai.core.service.AiRetrievalDomainService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/execution")
public class AiRetrievalController implements AiRetrievalExecutionApi {

    private final AiRetrievalDomainService aiRetrievalDomainService;

    public AiRetrievalController(AiRetrievalDomainService aiRetrievalDomainService) {
        this.aiRetrievalDomainService = aiRetrievalDomainService;
    }

    @Override
    @PostMapping("/retrieval/hybrid-search")
    public HybridSearchResponse hybridSearch(@RequestBody HybridSearchRequest request) {
        return aiRetrievalDomainService.hybridSearch(request);
    }

    @Override
    @PostMapping("/retrieval/intent-analyze")
    public IntentAnalyzeResponse analyzeIntent(@RequestBody IntentAnalyzeRequest request) {
        return aiRetrievalDomainService.analyzeIntent(request);
    }
}
