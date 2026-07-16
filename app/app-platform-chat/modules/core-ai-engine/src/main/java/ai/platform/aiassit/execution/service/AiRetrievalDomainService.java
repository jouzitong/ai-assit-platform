package ai.platform.aiassit.execution.service;

import ai.platform.aiassit.service.ai.api.dto.HybridSearchRequest;
import ai.platform.aiassit.service.ai.api.dto.HybridSearchResponse;

public interface AiRetrievalDomainService {

    HybridSearchResponse hybridSearch(HybridSearchRequest request);

}
