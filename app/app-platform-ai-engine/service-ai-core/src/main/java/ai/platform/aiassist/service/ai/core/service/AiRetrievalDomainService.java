package ai.platform.aiassist.service.ai.core.service;

import ai.platform.aiassist.service.ai.api.dto.HybridSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.HybridSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.IntentAnalyzeRequest;
import ai.platform.aiassist.service.ai.api.dto.IntentAnalyzeResponse;

public interface AiRetrievalDomainService {

    HybridSearchResponse hybridSearch(HybridSearchRequest request);

    IntentAnalyzeResponse analyzeIntent(IntentAnalyzeRequest request);
}
