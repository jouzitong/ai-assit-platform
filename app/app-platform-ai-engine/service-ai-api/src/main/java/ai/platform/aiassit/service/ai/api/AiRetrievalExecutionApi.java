package ai.platform.aiassit.service.ai.api;

import ai.platform.aiassit.service.ai.api.dto.HybridSearchRequest;
import ai.platform.aiassit.service.ai.api.dto.HybridSearchResponse;
import ai.platform.aiassit.service.ai.api.dto.IntentAnalyzeRequest;
import ai.platform.aiassit.service.ai.api.dto.IntentAnalyzeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * AI 检索执行 API（HTTP/Feign）。
 */
@FeignClient(
        name = "aiEngine",
        contextId = "platformAiRetrievalEngineClient",
        path = "/aiEngine")
@Deprecated // AiKnowledgeApi
public interface AiRetrievalExecutionApi {

    @PostMapping("/api/v1/ai/execution/retrieval/hybrid-search")
    HybridSearchResponse hybridSearch(@RequestBody HybridSearchRequest request);

    @PostMapping("/api/v1/ai/execution/retrieval/intent-analyze")
    IntentAnalyzeResponse analyzeIntent(@RequestBody IntentAnalyzeRequest request);
}
